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


            val result = when (loadType) {

                LoadType.REFRESH -> {


                    val id = postRemoteKeyDao.max()

                    if (id == null) {
                        service.getLatest(state.config.pageSize)
                    } else {
                        service.getAfter(id, state.config.pageSize)
                    }
                }


                LoadType.APPEND -> {

                    val id = postRemoteKeyDao.min() ?: return MediatorResult.Success(false)
                    service.getBefore(id, state.config.pageSize)

                }
                else -> {
                    return MediatorResult.Success(false)
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
                        //postDao.clear()

                        if (!data.isNullOrEmpty()) {

                            if (postDao.isEmpty()) {
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
                            } else {
                                postRemoteKeyDao.insert(
                                    PostRemoteKeyEntity(
                                        PostRemoteKeyEntity.KeyType.AFTER,
                                        data.first().id
                                    )
                                )
                            }
                        }

                    }

                    LoadType.APPEND -> {
                        if (!data.isNullOrEmpty()) {
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
                    else -> {

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