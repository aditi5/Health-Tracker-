from django.conf import settings
from django.core.validators import MinValueValidator, MaxValueValidator
from django.db import models


class Profile(models.Model):
    user = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    display_name = models.CharField(max_length=80, blank=True)
    timezone = models.CharField(max_length=64, default="UTC")
    # User-editable starting assumptions, not measured or clinical predictions.
    cycle_length = models.PositiveSmallIntegerField(default=28, validators=[MinValueValidator(1), MaxValueValidator(365)])
    period_length = models.PositiveSmallIntegerField(default=5, validators=[MinValueValidator(1), MaxValueValidator(365)])


class Period(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    start = models.DateField()
    end = models.DateField(null=True, blank=True)

    class Meta:
        ordering = ["-start"]
        constraints = [
            models.UniqueConstraint(fields=["user", "start"], name="unique_period_start"),
            models.CheckConstraint(condition=models.Q(end__isnull=True) | models.Q(end__gte=models.F("start")), name="period_end_after_start"),
        ]


class DailyLog(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    date = models.DateField()
    flow = models.CharField(max_length=12, blank=True, choices=[(name, name) for name in ["none", "spotting", "light", "medium", "heavy"]])
    symptoms = models.JSONField(default=list)
    mood = models.CharField(max_length=16, blank=True, choices=[(name, name) for name in ["calm", "happy", "sensitive", "sad", "anxious", "irritable"]])
    notes = models.TextField(blank=True, max_length=2000)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-date"]
        constraints = [models.UniqueConstraint(fields=["user", "date"], name="unique_daily_log")]
