from datetime import date, datetime
from zoneinfo import ZoneInfo

from django.contrib.auth import authenticate
from django.contrib.auth.models import User
from django.db import IntegrityError, transaction
from django.utils import timezone
from rest_framework import generics, serializers, status, viewsets
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken

from .analysis import cycle_summary
from .models import DailyLog, Period, Profile
from .serializers import DailyLogSerializer, PeriodSerializer, ProfileSerializer, RegisterSerializer, SYMPTOMS


class RegisterView(APIView):
    permission_classes = [AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "auth"

    def post(self, request):
        serializer = RegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        try:
            with transaction.atomic():
                user = User.objects.create_user(username=data["email"], email=data["email"], password=data["password"])
                Profile.objects.create(user=user, display_name=data.get("display_name", ""))
        except IntegrityError:
            raise serializers.ValidationError({"email": "An account already exists for this email."})
        refresh = RefreshToken.for_user(user)
        return Response({"access": str(refresh.access_token), "refresh": str(refresh)}, status=status.HTTP_201_CREATED)


class LoginView(APIView):
    permission_classes = [AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "auth"

    def post(self, request):
        class Credentials(serializers.Serializer):
            email = serializers.EmailField()
            password = serializers.CharField(trim_whitespace=False)
        serializer = Credentials(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        user = authenticate(username=data["email"].lower(), password=data["password"])
        if not user:
            return Response({"detail": "Email or password is incorrect."}, status=401)
        refresh = RefreshToken.for_user(user)
        return Response({"access": str(refresh.access_token), "refresh": str(refresh)})


class ProfileView(generics.RetrieveUpdateAPIView):
    serializer_class = ProfileSerializer
    http_method_names = ["get", "patch", "head", "options"]

    def get_object(self):
        return Profile.objects.get(user=self.request.user)


class PeriodViewSet(viewsets.ModelViewSet):
    serializer_class = PeriodSerializer

    def get_queryset(self):
        return Period.objects.filter(user=self.request.user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class DailyLogView(APIView):
    def parse_date(self, value):
        try:
            return date.fromisoformat(value)
        except ValueError:
            raise serializers.ValidationError({"date": "Use YYYY-MM-DD."})

    def get(self, request, day):
        log = DailyLog.objects.filter(user=request.user, date=self.parse_date(day)).first()
        if not log:
            return Response({"detail": "No entry for this day."}, status=404)
        return Response(DailyLogSerializer(log).data)

    def put(self, request, day):
        day = self.parse_date(day)
        serializer = DailyLogSerializer(data={**request.data, "date": day})
        serializer.is_valid(raise_exception=True)
        data = {"flow": "", "symptoms": [], "mood": "", "notes": "", **serializer.validated_data}
        data.pop("date")
        log, created = DailyLog.objects.update_or_create(user=request.user, date=day, defaults=data)
        return Response(DailyLogSerializer(log).data, status=201 if created else 200)

    def delete(self, request, day):
        DailyLog.objects.filter(user=request.user, date=self.parse_date(day)).delete()
        return Response(status=204)


class SummaryView(APIView):
    def get(self, request):
        profile = Profile.objects.get(user=request.user)
        today = timezone.now().astimezone(ZoneInfo(profile.timezone)).date()
        return Response(cycle_summary(Period.objects.filter(user=request.user), profile, today))


class CalendarView(APIView):
    def get(self, request):
        value = request.query_params.get("month", timezone.now().strftime("%Y-%m"))
        try:
            month = datetime.strptime(value, "%Y-%m").date().replace(day=1)
        except ValueError:
            raise serializers.ValidationError({"month": "Use YYYY-MM."})
        logs = DailyLog.objects.filter(user=request.user, date__year=month.year, date__month=month.month)
        return Response({"month": value, "logs": DailyLogSerializer(logs, many=True).data,
                         "periods": PeriodSerializer(Period.objects.filter(user=request.user), many=True).data})


class CatalogView(APIView):
    def get(self, request):
        return Response({"symptoms": SYMPTOMS, "flows": ["none", "spotting", "light", "medium", "heavy"],
                         "moods": ["calm", "happy", "sensitive", "sad", "anxious", "irritable"]})


class AccountView(APIView):
    def get(self, request):
        response = Response({
            "profile": ProfileSerializer(Profile.objects.get(user=request.user)).data,
            "periods": PeriodSerializer(Period.objects.filter(user=request.user), many=True).data,
            "logs": DailyLogSerializer(DailyLog.objects.filter(user=request.user), many=True).data,
        })
        response["Content-Disposition"] = 'attachment; filename="health-data.json"'
        return response

    def delete(self, request):
        request.user.delete()
        return Response(status=204)
