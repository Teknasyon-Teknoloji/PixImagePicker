package io.ak1.pix.ui.camera

import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import io.ak1.pix.R
import io.ak1.pix.databinding.FragmentCameraBinding
import io.ak1.pix.helpers.CameraXManager
import io.ak1.pix.helpers.Results
import io.ak1.pix.helpers.Status
import io.ak1.pix.helpers.color
import io.ak1.pix.helpers.hide
import io.ak1.pix.helpers.hideStatusBar
import io.ak1.pix.helpers.permissionsFilter
import io.ak1.pix.helpers.setDrawableIconForFlash
import io.ak1.pix.helpers.setupClickControls
import io.ak1.pix.helpers.setupScreenForCamera
import io.ak1.pix.helpers.show
import io.ak1.pix.models.Img
import io.ak1.pix.models.Options
import io.ak1.pix.ui.imagepicker.ImagePickerFragment
import io.ak1.pix.utility.ARG_PARAM_PIX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

/**
 * Created By Akshay Sharma on 17,June,2021
 * https://ak1.io
 */
class CameraFragment(private val resultCallback: ((Results) -> Unit)? = null) : Fragment() {

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
            it.setupScreenForCamera()
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
        binding.gridLayout.textAdditionalDescription.text = options.additionalBottomTextDescription
        with(binding.cameraTopBarLayout.closeImage) {
            isVisible = options.cameraScreenBackButtonIcon != null
            options.cameraScreenBackButtonIcon?.let {
                setImageDrawable(ContextCompat.getDrawable(context, it))
            }
        }
        cameraXManager = CameraXManager(binding.viewFinder, context, options).apply {
            startCamera()
        }
        observeSelectionList()
        setupControls()
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
                triggerResultCallback(
                    contentSet = set,
                    status = Status.SUCCESS
                )
            }
        }

        model.onBackPressedResult.observe(requireActivity()) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let { set ->
                triggerResultCallback(
                    contentSet = set,
                    status = Status.BACK_PRESSED
                )
            }
        }
    }

    private fun triggerResultCallback(
        contentSet: MutableSet<Img>,
        status: Status
    ) {
        model.selectionList.postValue(HashSet())
        options.preSelectedUrls.clear()
        val results = contentSet.map { it.contentUrl }
        resultCallback?.invoke(
            Results(
                data = results,
                status = status
            )
        )
    }

    private fun setupControls() {
        binding.setupClickControls(model, cameraXManager, options) { int, uri ->
            when (int) {
                0 -> model.returnObjects()
                1 -> model.onBackPressed()
                2 -> openGalleryScreen()
                3 -> {
                    var connection: MediaScannerConnection? = null
                    var client: MediaScannerConnection.MediaScannerConnectionClient?
                    client = object : MediaScannerConnection.MediaScannerConnectionClient {
                        override fun onScanCompleted(path: String, uri: Uri) {
                            connection?.disconnect()
                            client = null
                            model.selectionList.value?.add(Img(contentUrl = uri))
                            model.returnObjects()

                        }

                        override fun onMediaScannerConnected() {
                            connection?.scanFile(uri.path, null)
                        }
                    }
                    connection = MediaScannerConnection(requireContext(), client)
                    connection.apply {
                        connect()
                    }
                }
            }
        }
    }

    private fun openGalleryScreen() {
        val imagePickerResultCallback: ((Results) -> Unit) = { result: Results ->
            when (result.status) {
                Status.SUCCESS -> resultCallback?.invoke(result)
                Status.BACK_PRESSED -> childFragmentManager.popBackStack()
            }
        }
        val imagePickerFragment = ImagePickerFragment(imagePickerResultCallback).apply {
            arguments = Bundle().apply {
                putParcelable(ARG_PARAM_PIX, options)
            }
        }
        childFragmentManager.commit {
            add(R.id.container, imagePickerFragment, "")
            addToBackStack(null)
        }
    }

    private fun CameraXManager.startCamera() {
        setUpCamera(binding)
        binding.cameraTopBarLayout.flashButton.show()
        binding.setDrawableIconForFlash(options)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

}