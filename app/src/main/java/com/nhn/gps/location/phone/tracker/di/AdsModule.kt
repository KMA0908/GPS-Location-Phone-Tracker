package com.nhn.gps.location.phone.tracker.di

import com.leansoft.ads.AdConfig
import com.leansoft.ads.ui.language.LeansoftLanguageInterface
import com.leansoft.ads.ui.onboarding.LeansoftOnboardingInterface
import com.leansoft.ads.ui.splash.LeansoftSplashInterface
import com.leansoft.ads.ui.uninstall.LeansoftUninstallInterface
import com.leansoft.ads.ui.welcome_back.LeansoftWelcomeBackInterface
import com.nhn.gps.location.phone.tracker.ads.GpsAdConfig
import com.nhn.gps.location.phone.tracker.ads.GpsLanguageImpl
import com.nhn.gps.location.phone.tracker.ads.GpsOnboardingImpl
import com.nhn.gps.location.phone.tracker.ads.GpsSplashImpl
import com.nhn.gps.location.phone.tracker.ads.GpsUninstallImpl
import com.nhn.gps.location.phone.tracker.ads.GpsWelcomeBackImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdsModule {
    @Provides @Singleton fun provideAdConfig(config: GpsAdConfig): AdConfig = config
    @Provides @Singleton fun provideSplash(impl: GpsSplashImpl): LeansoftSplashInterface = impl
    @Provides @Singleton fun provideLanguage(): LeansoftLanguageInterface = GpsLanguageImpl()
    @Provides @Singleton fun provideOnboarding(): LeansoftOnboardingInterface = GpsOnboardingImpl()
    @Provides @Singleton fun provideUninstall(): LeansoftUninstallInterface = GpsUninstallImpl()
    @Provides @Singleton fun provideWelcomeBack(): LeansoftWelcomeBackInterface = GpsWelcomeBackImpl()
}
