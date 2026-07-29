package com.nhn.gps.location.phone.tracker.di

import com.nhn.gps.location.phone.tracker.data.repository.FriendRepository
import com.nhn.gps.location.phone.tracker.data.repository.FriendRepositoryImpl
import com.nhn.gps.location.phone.tracker.data.repository.LocationRepository
import com.nhn.gps.location.phone.tracker.data.repository.LocationRepositoryImpl
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
}
