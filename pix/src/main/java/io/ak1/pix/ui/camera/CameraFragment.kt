package io.ak1.pix.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.core.net.toFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import io.ak1.pix.R
import io.ak1.pix.databinding.FragmentCameraBinding
import io.ak1.pix.helpers.*
import io.ak1.pix.models.Img
import io.ak1.pix.models.Options
import io.ak1.pix.utility.ARG_PARAM_PIX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Created By Akshay Sharma on 17,June,2021
 * https://ak1.io
 */

class CameraFragment(private val resultCallback: ((PixEventCallback.Results) -> Unit)? = null) : Fragment() {

    private val model: CameraViewModel by viewModels()
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var permReqLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                binding.permissionsLayout.permissionsLayout.hide()
                binding.gridLayout.gridLayout.show()
                initialise(requireActivity())
            } else {
                binding.gridLayout.gridLayout.hide()
                binding.permissionsLayout.permissionsLayout.show()
            }
        }

    private var cameraXManager: CameraXManager? = null
    private lateinit var options: Options
    private var scope = CoroutineScope(Dispatchers.IO)
    private var colorPrimaryDark = 0

    override fun onResume() {
        super.onResume()
        requireActivity().hideStatusBar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        options = arguments?.getParcelable(ARG_PARAM_PIX) ?: Options()
        requireActivity().let {
            it.setupScreen()
            it.actionBar?.hide()
            colorPrimaryDark = it.color(R.color.primary_color_pix)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = run {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().setup()
    }

    private fun FragmentActivity.setup() {
        permissions()
        reSetup(this)
    }

    private fun permissions() {
        binding.permissionsLayout.permissionButton.setOnClickListener {
            permReqLauncher.permissionsFilter(requireActivity(), options) {
                initialise(requireActivity())
            }
        }
    }

    private fun reSetup(context: FragmentActivity) {
        permReqLauncher.permissionsFilter(context, options) {
            initialise(context)
        }
    }


    private fun initialise(context: FragmentActivity) {
        binding.permissionsLayout.permissionsLayout.hide()
        binding.gridLayout.gridLayout.show()
        cameraXManager = CameraXManager(binding.viewFinder, context, options).apply {
            startCamera()
        }
        observeSelectionList()
        setupControls()
        backPressController()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        model.selectionList.removeObservers(requireActivity())
        model.callResults.removeObservers(requireActivity())
        _binding = null
    }

    private fun observeSelectionList() {
        model.setOptions(options)

        model.callResults.observe(requireActivity()) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let { set ->
                model.selectionList.postValue(HashSet())
                options.preSelectedUrls.clear()
                val results = set.map { it.contentUrl }
                resultCallback?.invoke(PixEventCallback.Results(results))
                PixBus.returnObjects(
                    event = PixEventCallback.Results(
                        results,
                        PixEventCallback.Status.SUCCESS
                    )
                )
            }
        }
    }

    private fun backPressController() {
        CoroutineScope(Dispatchers.Main).launch {
            PixBus.on(this) {
                val list = model.selectionList.value ?: HashSet()
                when {
                    list.size > 0 -> {
                        for (img in list) {
                            mainImageAdapter.select(false, img.position)
                        }
                        model.selectionList.postValue(HashSet())
                    }
                    else -> {
                        model.returnObjects()
                    }
                }
            }
        }
    }

    private fun setupControls() {
        binding.setupClickControls(model, cameraXManager, options) { int, uri ->
            when (int) {
                0 -> model.returnObjects()
                3 -> requireActivity().scanPhoto(uri.toFile()) {
                    if (model.selectionList.value.isNullOrEmpty()) {
                        model.selectionList.value?.add(Img(contentUrl = it))
                        scope.cancel(CancellationException("canceled intentionally"))
                        model.returnObjects()
                        return@scanPhoto
                    }
                    model.selectionList.value?.add(Img(contentUrl = it))
                }
            }
        }
    }


    private fun CameraXManager.startCamera() {
        setUpCamera(binding)
        binding.gridLayout.controlsLayout.flashButton.show()
        binding.setDrawableIconForFlash(options)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

}