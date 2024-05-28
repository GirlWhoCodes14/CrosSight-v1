package com.crossight.fragments
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.crossight.R

class ErrorFragment : Fragment() {

    private val cameraPermissionRequestCode = 1001

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.permission_error, container, false)

        val button: Button = view.findViewById(R.id.button)
        button.setOnClickListener {
            // Launch the permission request. The result will be handled by the launcher's callback.
            requestCameraPermission()
        }

        return view
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode
            )
        } else {
            Toast.makeText(requireContext(),"Permission granted",Toast.LENGTH_LONG).show()
        }
    }

}