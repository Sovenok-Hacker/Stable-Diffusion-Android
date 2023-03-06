package com.shifthackz.aisdv1.presentation.screen.gallery.list

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.shifthackz.aisdv1.core.common.schedulers.SchedulersProvider
import com.shifthackz.aisdv1.core.imageprocessing.Base64ToBitmapConverter.Input
import com.shifthackz.aisdv1.core.imageprocessing.Base64ToBitmapConverter.Output
import com.shifthackz.aisdv1.core.imageprocessing.Base64ToBitmapProcessor
import com.shifthackz.aisdv1.domain.usecase.gallery.GetGalleryPageUseCase
import com.shifthackz.aisdv1.presentation.utils.Constants
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

typealias GalleryPagedResult = PagingSource.LoadResult<Int, GalleryGridItemUi>

class GalleryPagingSource(
    private val getGalleryPageUseCase: GetGalleryPageUseCase,
    private val base64ToBitmapConverter: Base64ToBitmapProcessor,
    private val schedulersProvider: SchedulersProvider,
) : RxPagingSource<Int, GalleryGridItemUi>() {

    override fun getRefreshKey(state: PagingState<Int, GalleryGridItemUi>): Int = FIRST_KEY

    override fun loadSingle(params: LoadParams<Int>) = loadSingleImpl(params)

    private fun loadSingleImpl(params: LoadParams<Int>): Single<GalleryPagedResult> {
        val pageSize = params.loadSize
        val pageNext = params.key ?: FIRST_KEY
        return getGalleryPageUseCase(
            limit = pageSize,
            offset = pageNext * Constants.PAGINATION_PAYLOAD_SIZE,
        )
            .subscribeOn(schedulersProvider.io)
            .flatMapObservable { Observable.fromIterable(it) }
            .map { ai -> ai.id to ai.image }
            .map { (id, base64) -> id to Input(base64) }
            .flatMapSingle { (id, input) ->
                base64ToBitmapConverter(input).map { out -> id to out }
            }
            .map(::mapOutputToUi)
            .toList()
            .map { payload ->
                LoadResult.Page(
                    data = payload,
                    prevKey = if (pageNext == FIRST_KEY) null else pageNext - 1,
                    nextKey = if (payload.isEmpty()) null else pageNext + 1,
                ).let(GalleryPagingSource::Wrapper)
            }
            .onErrorReturn { t -> Wrapper(LoadResult.Error(t)) }
            .map(Wrapper::loadResult)
    }

    private fun mapOutputToUi(output: Pair<Long, Output>) = GalleryGridItemUi(
        output.first,
        output.second.bitmap,
    )

    private data class Wrapper(val loadResult: GalleryPagedResult)

    companion object {
        const val FIRST_KEY = 0
    }
}