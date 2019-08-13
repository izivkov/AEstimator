package org.avmedia.ageestimator.fragments

import android.app.Activity
import androidx.navigation.Navigation

open class FragmentNavigator () {

    companion object {
        @JvmStatic

        private lateinit var actionType: String

        open fun navigate(activity: Activity, fragmentId: Int, path: String) {
            Navigation.findNavController(activity, fragmentId).navigate(
                    CameraFragmentDirections.actionCameraToGallery(path, this.actionType))
        }

        fun setActionType (actionType: String) {
            this.actionType = actionType
        }
    }
}