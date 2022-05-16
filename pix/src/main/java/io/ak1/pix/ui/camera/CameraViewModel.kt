package io.ak1.pix.ui.camera

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.ak1.pix.models.Img
import io.ak1.pix.models.Options

internal class CameraViewModel : ViewModel() {

    val selectionList by lazy { MutableLiveData<MutableSet<Img>>(HashSet()) }
    val callResults by lazy { MutableLiveData<Event<MutableSet<Img>>>() }

    private lateinit var options: Options

    fun returnObjects() = callResults.postValue(Event(selectionList.value ?: HashSet()))

    fun setOptions(options: Options) {
        this.options = options
    }
}

open class Event<out T>(private val content: T) {

    private var hasBeenHandled = false

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandledOrReturnNull(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }
}
