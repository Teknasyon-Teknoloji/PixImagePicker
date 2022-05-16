package io.ak1.pix.ui.imagepicker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.ak1.pix.helpers.LocalResourceManager
import io.ak1.pix.interfaces.PixLifecycle
import io.ak1.pix.ui.camera.Event
import io.ak1.pix.models.Img
import io.ak1.pix.models.ModelList
import io.ak1.pix.models.Options
import kotlinx.coroutines.delay

internal class ImagePickerViewModel : ViewModel(), PixLifecycle {
    private val allImagesList by lazy { MutableLiveData(ModelList()) }
    private val _longSelection: MutableLiveData<Boolean> = MutableLiveData(false)
    val longSelection: LiveData<Boolean> = _longSelection
    private val _selectionList by lazy { MutableLiveData<MutableSet<Img>>(HashSet()) }
    val selectionList: LiveData<MutableSet<Img>> = _selectionList
    private val _callResults by lazy { MutableLiveData<Event<MutableSet<Img>>>() }
    val callResults: LiveData<Event<MutableSet<Img>>> = _callResults

    val longSelectionValue: Boolean
        get() {
            return _longSelection.value ?: false
        }
    val selectionListSize: Int
        get() {
            return _selectionList.value?.size ?: 0
        }
    val imageList: LiveData<ModelList> = allImagesList

    private lateinit var options: Options

    suspend fun retrieveImages(localResourceManager: LocalResourceManager) {
        val sizeInitial = 100
        _selectionList.value?.clear()
        allImagesList.postValue(
            localResourceManager.retrieveMedia(
                limit = sizeInitial,
                mode = options.mode
            )
        )
        delay(100)
        val modelList = localResourceManager.retrieveMedia(
            start = sizeInitial + 1,
            mode = options.mode
        )
        if (modelList.list.isNotEmpty()) {
            allImagesList.postValue(modelList)
        }
    }

    override fun onImageSelected(element: Img?, position: Int, callback: (Boolean) -> Boolean) {
        if (longSelectionValue) {
            _selectionList.value?.apply {
                updateSelectionList(element, position, callback)
            }
            changeSelectionList(_selectionList.value)
        } else {
            element?.let { img ->
                img.position = position
                _selectionList.value?.add(img)
                returnObjects()
            }
        }

    }

    override fun onImageLongSelected(element: Img?, position: Int, callback: (Boolean) -> Boolean) {
        if (options.count > 1) {
            changeLongSelectionStatus(true)
            updateSelectionList(element, position, callback)
            changeSelectionList(_selectionList.value)
        }
    }

    private fun updateSelectionList(element: Img?, position: Int, callback: (Boolean) -> Boolean){
        _selectionList.value?.apply {
            if (contains(element)) {
                remove(element)
                callback(false)
            } else if (callback(true)) {
                element?.let {  img ->
                    img.position = position
                    add(img)
                }
            }
        }
    }

    fun returnObjects() = _callResults.postValue(Event(_selectionList.value ?: HashSet()))

    fun setOptions(options: Options) {
        this.options = options
    }

    fun changeLongSelectionStatus(status: Boolean){
        _longSelection.postValue(status)
    }

    fun changeSelectionList(newList: MutableSet<Img>?) {
        _selectionList.postValue(newList?: HashSet())
    }
}