from datetime import timedelta
from statistics import median


def cycle_summary(periods, profile, today):
    periods = sorted((period for period in periods if period.start <= today), key=lambda period: period.start)
    intervals = [(right.start - left.start).days for left, right in zip(periods, periods[1:])]
    length = round(median(intervals)) if intervals else profile.cycle_length
    durations = [(period.end - period.start).days + 1 for period in periods if period.end]
    duration = round(median(durations)) if durations else profile.period_length
    latest = periods[-1] if periods else None
    next_start = latest.start + timedelta(days=length) if latest else None
    return {
        "today": today.isoformat(),
        "cycle_day": (today - latest.start).days + 1 if latest else None,
        "estimated_cycle_length": length,
        "estimated_period_length": duration,
        "next_period_start": next_start.isoformat() if next_start else None,
        "days_until_period": (next_start - today).days if next_start else None,
        "completed_intervals": len(intervals),
        "cycle_variation": max(intervals) - min(intervals) if intervals else None,
        "estimate_basis": "median_logged_intervals" if intervals else "user_settings",
        "notice": "Calendar estimate only. Not for contraception, diagnosis, or confirming ovulation.",
    }
