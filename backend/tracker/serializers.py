from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from django.contrib.auth.models import User
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as DjangoValidationError
from rest_framework import serializers

from .models import DailyLog, Period, Profile

SYMPTOMS = ["cramps", "headache", "bloating", "breast_tenderness", "fatigue", "acne", "nausea", "backache", "hot_flashes", "poor_sleep"]


class RegisterSerializer(serializers.Serializer):
    email = serializers.EmailField(max_length=150)
    password = serializers.CharField(write_only=True, trim_whitespace=False)
    display_name = serializers.CharField(max_length=80, required=False, allow_blank=True)

    def validate_email(self, value):
        value = value.lower()
        if User.objects.filter(username=value).exists():
            raise serializers.ValidationError("An account already exists for this email.")
        return value

    def validate(self, attrs):
        try:
            validate_password(attrs["password"], User(username=attrs["email"], email=attrs["email"]))
        except DjangoValidationError as error:
            raise serializers.ValidationError({"password": error.messages})
        return attrs


class ProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = Profile
        fields = ["display_name", "timezone", "cycle_length", "period_length"]

    def validate_timezone(self, value):
        try:
            ZoneInfo(value)
        except (ValueError, ZoneInfoNotFoundError):
            raise serializers.ValidationError("Use a valid IANA timezone.")
        return value

    def validate(self, attrs):
        cycle = attrs.get("cycle_length", self.instance.cycle_length)
        period = attrs.get("period_length", self.instance.period_length)
        if period > cycle:
            raise serializers.ValidationError({"period_length": "Period length cannot exceed cycle length."})
        return attrs


class PeriodSerializer(serializers.ModelSerializer):
    class Meta:
        model = Period
        fields = ["id", "start", "end"]
        read_only_fields = ["id"]

    def validate(self, attrs):
        start = attrs.get("start", self.instance.start if self.instance else None)
        end = attrs.get("end", self.instance.end if self.instance else None)
        if end and end < start:
            raise serializers.ValidationError({"end": "End must be on or after start."})
        existing = Period.objects.filter(user=self.context["request"].user)
        if self.instance:
            existing = existing.exclude(pk=self.instance.pk)
        if existing.filter(start=start).exists():
            raise serializers.ValidationError({"start": "A period with this start date already exists."})
        return attrs


class DailyLogSerializer(serializers.ModelSerializer):
    symptoms = serializers.ListField(child=serializers.ChoiceField(choices=SYMPTOMS), max_length=len(SYMPTOMS), required=False)

    class Meta:
        model = DailyLog
        fields = ["date", "flow", "symptoms", "mood", "notes", "updated_at"]
        read_only_fields = ["updated_at"]

    def validate_symptoms(self, value):
        return list(dict.fromkeys(value))
