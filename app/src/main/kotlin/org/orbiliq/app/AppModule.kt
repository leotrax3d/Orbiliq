package org.orbiliq.app

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.orbiliq.core.data.UnconfiguredConversationRepository
import org.orbiliq.core.domain.ConversationRepository

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun conversations(): ConversationRepository = UnconfiguredConversationRepository()
}
