from django.urls import include, path
from rest_framework.routers import DefaultRouter
from rest_framework_simplejwt.views import TokenBlacklistView, TokenRefreshView
from tracker import views

router = DefaultRouter()
router.register("periods", views.PeriodViewSet, basename="period")
urlpatterns = [
    path("api/v1/auth/register/", views.RegisterView.as_view()),
    path("api/v1/auth/login/", views.LoginView.as_view()),
    path("api/v1/auth/refresh/", TokenRefreshView.as_view()),
    path("api/v1/auth/logout/", TokenBlacklistView.as_view()),
    path("api/v1/profile/", views.ProfileView.as_view()),
    path("api/v1/logs/<str:day>/", views.DailyLogView.as_view()),
    path("api/v1/summary/", views.SummaryView.as_view()),
    path("api/v1/calendar/", views.CalendarView.as_view()),
    path("api/v1/catalog/", views.CatalogView.as_view()),
    path("api/v1/account/", views.AccountView.as_view()),
    path("api/v1/", include(router.urls)),
]
