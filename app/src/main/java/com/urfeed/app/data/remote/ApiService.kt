package com.urfeed.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/auth/register") suspend fun register(@Body body: AuthRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @GET("api/auth/me") suspend fun me(): UserDto
    @GET("api/users/search") suspend fun searchUsers(@Query("q") query: String): List<UserDto>
    @GET("api/users/me") suspend fun myProfile(): UserDto
    @PUT("api/users/me") suspend fun updateProfile(@Body body: Map<String, @JvmSuppressWildcards Any?>): UserDto
    @POST("api/users/{userId}/follow") suspend fun follow(@Path("userId") userId: String): FollowResponse
    @DELETE("api/users/{userId}/follow") suspend fun unfollow(@Path("userId") userId: String): FollowResponse
    @GET("api/users/{userId}/followers") suspend fun followers(@Path("userId") userId: String): List<UserDto>
    @GET("api/users/{userId}/following") suspend fun following(@Path("userId") userId: String): List<UserDto>
    @POST("api/posts") suspend fun createPost(@Body body: PostCreateRequest): PostDto
    @GET("api/posts/feed") suspend fun feed(@Query("following") following: Boolean = false, @Query("tag") tag: String? = null, @Query("page") page: Int = 1): List<PostDto>
    @GET("api/posts/{postId}") suspend fun post(@Path("postId") postId: String): PostDto
    @DELETE("api/posts/{postId}") suspend fun deletePost(@Path("postId") postId: String)
    @POST("api/posts/{postId}/like") suspend fun like(@Path("postId") postId: String)
    @DELETE("api/posts/{postId}/like") suspend fun unlike(@Path("postId") postId: String)
    @POST("api/posts/{postId}/comments") suspend fun comment(@Path("postId") postId: String, @Body body: Map<String, String>)
    @POST("api/statuses") suspend fun createStatus(@Body body: StatusCreateRequest): StatusDto
    @GET("api/statuses/feed") suspend fun statusFeed(@Query("following") following: Boolean = false): List<StatusDto>
    @GET("api/statuses/{statusId}") suspend fun status(@Path("statusId") statusId: String): StatusDto
    @DELETE("api/statuses/{statusId}") suspend fun deleteStatus(@Path("statusId") statusId: String)
    @GET("api/search") suspend fun search(@Query("q") query: String, @Query("content_type") contentType: String = "all", @Query("tag") tag: String? = null, @Query("page") page: Int = 1): SearchResponseDto
}
