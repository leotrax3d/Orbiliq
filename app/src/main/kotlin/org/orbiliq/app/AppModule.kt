package org.orbiliq.app

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.orbiliq.core.domain.ConversationRepository

/** Application-owned repository access for workers and instrumentation. */
@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface ConversationRepositoryEntryPoint { fun conversations(): ConversationRepository }

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun conversations(@ApplicationContext context: Context): ConversationRepository = developmentConversationRepository(context)
}
