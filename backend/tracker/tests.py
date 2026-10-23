from datetime import date, datetime, timezone
from types import SimpleNamespace
from unittest.mock import patch

from django.contrib.auth.models import User
from django.core.cache import cache
from django.test import SimpleTestCase
from rest_framework.test import APITestCase

from .analysis import cycle_summary
from .models import DailyLog, Period, Profile


class CycleSummaryTests(SimpleTestCase):
    def setUp(self):
        self.profile = SimpleNamespace(cycle_length=28, period_length=5)

    def test_no_history_has_no_invented_dates(self):
        result = cycle_summary([], self.profile, date(2026, 10, 23))
        self.assertIsNone(result["next_period_start"])
        self.assertIsNone(result["cycle_day"])
        self.assertEqual(result["estimate_basis"], "user_settings")

    def test_median_uses_start_intervals_across_year_boundary(self):
        periods = [SimpleNamespace(start=date(2025, 12, 20), end=date(2025, 12, 24)),
                   SimpleNamespace(start=date(2026, 1, 19), end=date(2026, 1, 22)),
                   SimpleNamespace(start=date(2026, 2, 16), end=None)]
        result = cycle_summary(periods, self.profile, date(2026, 2, 20))
        self.assertEqual(result["estimated_cycle_length"], 29)
        self.assertEqual(result["next_period_start"], "2026-03-17")
        self.assertEqual(result["cycle_day"], 5)
        self.assertEqual(result["cycle_variation"], 2)

    def test_overdue_estimate_is_not_silently_moved_forward(self):
        period = SimpleNamespace(start=date(2026, 9, 1), end=None)
        result = cycle_summary([period], self.profile, date(2026, 10, 23))
        self.assertLess(result["days_until_period"], 0)
        self.assertEqual(result["next_period_start"], "2026-09-29")

    def test_future_period_is_not_used_to_predict_today(self):
        future = SimpleNamespace(start=date(2026, 12, 1), end=None)
        self.assertIsNone(cycle_summary([future], self.profile, date(2026, 10, 23))["cycle_day"])


class TrackerAPITests(APITestCase):
    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user("owner@example.com", password="sample-pass-for-tests-42")
        self.other = User.objects.create_user("other@example.com", password="sample-pass-for-tests-42")
        Profile.objects.create(user=self.owner)
        Profile.objects.create(user=self.other)
        self.client.force_authenticate(self.owner)

    def test_private_endpoints_require_login(self):
        self.client.force_authenticate(None)
        for path in ["profile/", "summary/", "calendar/", "periods/", "catalog/", "account/", "logs/2026-10-23/"]:
            with self.subTest(path=path):
                self.assertEqual(self.client.get("/api/v1/" + path).status_code, 401)

    def test_registration_password_validation_and_case_insensitive_login(self):
        self.client.force_authenticate(None)
        endpoint = "/api/v1/auth/register/"
        self.assertEqual(self.client.post(endpoint, {"email": "new@example.com", "password": "123"}).status_code, 400)
        response = self.client.post(endpoint, {"email": "NEW@example.com", "password": "sample-pass-for-tests-42"})
        self.assertEqual(response.status_code, 201)
        self.assertIn("access", response.data)
        self.assertEqual(self.client.post(endpoint, {"email": "new@example.com", "password": "sample-pass-for-tests-42"}).status_code, 400)
        self.assertEqual(self.client.post("/api/v1/auth/login/", {"email": "NEW@example.com", "password": "sample-pass-for-tests-42"}).status_code, 200)
        self.assertEqual(self.client.post("/api/v1/auth/login/", {"email": "new@example.com", "password": "wrong"}).status_code, 401)

    def test_period_data_cannot_cross_accounts(self):
        period = Period.objects.create(user=self.other, start=date(2026, 10, 1))
        self.assertEqual(self.client.get("/api/v1/periods/").data, [])
        for method in [self.client.get, self.client.patch, self.client.delete]:
            self.assertEqual(method(f"/api/v1/periods/{period.pk}/").status_code, 404)

    def test_period_validation_and_update(self):
        endpoint = "/api/v1/periods/"
        self.assertEqual(self.client.post(endpoint, {"start": "2026-10-02", "end": "2026-10-01"}).status_code, 400)
        response = self.client.post(endpoint, {"start": "2026-10-01"})
        self.assertEqual(response.status_code, 201)
        self.assertEqual(self.client.post(endpoint, {"start": "2026-10-01"}).status_code, 400)
        response = self.client.patch(f"{endpoint}{response.data['id']}/", {"end": "2026-10-05"})
        self.assertEqual(response.data["end"], "2026-10-05")

    def test_log_upsert_replaces_day_without_creating_duplicates(self):
        endpoint = "/api/v1/logs/2026-10-23/"
        self.assertEqual(self.client.get(endpoint).status_code, 404)
        response = self.client.put(endpoint, {"flow": "light", "symptoms": ["cramps", "cramps"], "notes": "sample"}, format="json")
        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["symptoms"], ["cramps"])
        self.assertEqual(self.client.put(endpoint, {"mood": "calm"}, format="json").status_code, 200)
        self.assertEqual(DailyLog.objects.filter(user=self.owner).count(), 1)
        self.assertEqual(self.client.get(endpoint).data["notes"], "")
        self.assertEqual(self.client.put(endpoint, {"symptoms": ["unknown"]}, format="json").status_code, 400)
        self.assertEqual(self.client.put("/api/v1/logs/invalid/", {}, format="json").status_code, 400)
        self.assertEqual(self.client.delete(endpoint).status_code, 204)

    def test_profile_validation(self):
        self.assertEqual(self.client.patch("/api/v1/profile/", {"timezone": "invalid"}).status_code, 400)
        self.assertEqual(self.client.patch("/api/v1/profile/", {"period_length": 30}).status_code, 400)
        self.assertEqual(self.client.patch("/api/v1/profile/", {"timezone": "Asia/Kolkata", "cycle_length": 30}).status_code, 200)

    def test_summary_uses_profile_local_date_at_midnight_boundary(self):
        profile = Profile.objects.get(user=self.owner)
        profile.timezone = "Asia/Kolkata"
        profile.save()
        Period.objects.create(user=self.owner, start=date(2026, 10, 24))
        with patch("tracker.views.timezone.now", return_value=datetime(2026, 10, 23, 20, tzinfo=timezone.utc)):
            response = self.client.get("/api/v1/summary/")
        self.assertEqual(response.data["today"], "2026-10-24")
        self.assertEqual(response.data["cycle_day"], 1)

    def test_calendar_filters_logs_and_owners(self):
        DailyLog.objects.create(user=self.owner, date=date(2026, 10, 23), mood="calm")
        DailyLog.objects.create(user=self.owner, date=date(2026, 9, 23))
        DailyLog.objects.create(user=self.other, date=date(2026, 10, 23))
        self.assertEqual(len(self.client.get("/api/v1/calendar/?month=2026-10").data["logs"]), 1)
        self.assertEqual(self.client.get("/api/v1/calendar/?month=2026-99").status_code, 400)

    def test_export_and_deletion_are_owner_scoped(self):
        Period.objects.create(user=self.owner, start=date(2026, 10, 1))
        Period.objects.create(user=self.other, start=date(2026, 10, 2))
        self.assertEqual(len(self.client.get("/api/v1/account/").data["periods"]), 1)
        self.assertEqual(self.client.delete("/api/v1/account/").status_code, 204)
        self.assertFalse(User.objects.filter(pk=self.owner.pk).exists())
        self.assertTrue(User.objects.filter(pk=self.other.pk).exists())

    def test_refresh_rotation_rejects_reused_token(self):
        self.client.force_authenticate(None)
        login = self.client.post("/api/v1/auth/login/", {"email": self.owner.username, "password": "sample-pass-for-tests-42"})
        old_refresh = login.data["refresh"]
        self.assertEqual(self.client.post("/api/v1/auth/refresh/", {"refresh": old_refresh}).status_code, 200)
        self.assertEqual(self.client.post("/api/v1/auth/refresh/", {"refresh": old_refresh}).status_code, 401)
