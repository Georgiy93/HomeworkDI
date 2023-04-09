package ru.netology.nmedia.repository

import android.content.SharedPreferences
import androidx.paging.*
import androidx.room.withTransaction
import retrofit2.HttpException

import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.application.NMediaApplication
import ru.netology.nmedia.dao.PostDao

import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.dto.PostRemoteKeyDao

import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.entity.PostRemoteKeyEntity
import ru.netology.nmedia.error.ApiError

import java.io.IOException


@OptIn(ExperimentalPagingApi::class)
class PostRemoteMediator(
    private val postDao: PostDao,
    private val service: ApiService,
    private val postRemoteKeyDao: PostRemoteKeyDao,
    private val appDb: AppDb,

    ) : RemoteMediator<Int, PostEntity>() {


    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>
    ): MediatorResult {
        try {
//            if(postDao.count()==0){
//                service.clear()}

            val result = when (loadType) {
                LoadType.REFRESH -> {

                    service.getLatest(state.config.pageSize)
                }
                LoadType.PREPEND -> {


                    val id = postRemoteKeyDao.max() ?: return MediatorResult.Success(false)
                    service.getAfter(id, state.config.pageSize)

                }

                LoadType.APPEND -> {
                    val id = postRemoteKeyDao.min() ?: return MediatorResult.Success(false)
                    service.getBefore(id, state.config.pageSize)
                }

            }
            if (!result.isSuccessful) {
                throw HttpException(result)
            }
            val data = result.body() ?: throw ApiError(
                result.code(), result.message()
            )
            appDb.withTransaction {

                when (loadType) {
                    LoadType.REFRESH -> {
                        println("1")


                        postDao.clear()
                        postRemoteKeyDao.insert(
                            listOf(
                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.AFTER,
                                    data.first().id,
                                ),
                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.BEFORE,
                                    data.last().id,
                                ),
                            )
                        )
                    }
                    LoadType.PREPEND -> {
                        println("1")
                        postRemoteKeyDao.insert(
                            listOf(
                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.AFTER,
                                    data.first().id,
                                ),

                                )
                        )
                    }
                    LoadType.APPEND -> {
                        println("1")
                        postRemoteKeyDao.insert(
                            listOf(

                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.BEFORE,
                                    data.last().id,
                                ),
                            )
                        )
                    }
                }

                postDao.insert(data.map(PostEntity.Companion::fromDto))
            }
            return MediatorResult.Success(
                data.isEmpty()
            )
        } catch (e: IOException) {
            return MediatorResult.Error(e)
        }
    }
}