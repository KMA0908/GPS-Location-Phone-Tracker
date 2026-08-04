package com.nhn.gps.location.phone.tracker.di

import com.nhn.gps.location.phone.tracker.data.repository.CountryRepository
import com.nhn.gps.location.phone.tracker.data.repository.CountryRepositoryImpl
import com.nhn.gps.location.phone.tracker.data.repository.FriendRepository
import com.nhn.gps.location.phone.tracker.data.repository.FriendRepositoryImpl
import com.nhn.gps.location.phone.tracker.data.repository.LocationRepository
import com.nhn.gps.location.phone.tracker.data.repository.LocationRepositoryImpl
import com.nhn.gps.location.phone.tracker.data.repository.PhoneLocatorRepository
import com.nhn.gps.location.phone.tracker.data.repository.PhoneLocatorRepositoryImpl
import com.nhn.gps.location.phone.tracker.data.repository.UserRepository
import com.nhn.gps.location.phone.tracker.data.repository.UserRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindFriendRepository(
        friendRepositoryImpl: FriendRepositoryImpl
    ): FriendRepository

    @Binds
    @Singleton
    abstract fun bindCountryRepository(
        countryRepositoryImpl: CountryRepositoryImpl
    ): CountryRepository

    @Binds
    @Singleton
    abstract fun bindPhoneLocatorRepository(
        phoneLocatorRepositoryImpl: PhoneLocatorRepositoryImpl
    ): PhoneLocatorRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository
}
