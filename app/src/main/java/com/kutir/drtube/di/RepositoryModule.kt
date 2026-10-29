package com.kutir.drtube.di

import android.content.Context
import com.kutir.drtube.BuildConfig
import com.kutir.drtube.data.api.PipedApiService
import com.kutir.drtube.data.api.YoutubeApiService
import com.kutir.drtube.data.repository.VideoRepository
import com.kutir.drtube.data.repository.VideoRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideYoutubeApiService(): YoutubeApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                // If token exists in request tag or header, use it
                val token = request.header("Authorization")
                val newRequest = if (token != null) {
                    request.newBuilder().build()
                } else {
                    request
                }
                chain.proceed(newRequest)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YoutubeApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePipedApiService(): PipedApiService {
        return Retrofit.Builder()
            .baseUrl("https://pipedapi.ducks.party/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PipedApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideVideoRepository(
        pipedApi: PipedApiService,
        youtubeApi: YoutubeApiService,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        @ApplicationContext context: Context
    ): VideoRepository {
        return VideoRepositoryImpl(
            pipedApi,
            youtubeApi,
            BuildConfig.YOUTUBE_API_KEY,
            firestore,
            auth,
            context
        )
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
