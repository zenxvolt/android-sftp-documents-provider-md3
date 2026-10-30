package com.island.androidsftpdocumentsprovider.provider

import lu.knaff.alain.saf_sftp.R
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.Uri
import android.provider.DocumentsContract
import android.view.View

/**
 * Common activity superclass with helpful methods for documents providers
 */
abstract class ProviderActivity : AppCompatActivity() {
    fun getAuthority(): String {
	return getString(R.string.authority)
    }

    fun notifyChange(mode: Int) {
	val uri:Uri = DocumentsContract.buildRootsUri(getAuthority())
	contentResolver.notifyChange(uri, null, mode)
    }

    /**
     * Keeps the screen content clear of the navigation bar, display cutout
     * and on-screen keyboard (Android 15+ draws edge to edge). The status
     * bar is left to the AppBarLayout (android:fitsSystemWindows), so the
     * toolbar can extend underneath it.
     *
     * Call this after setContentView(), passing the root view of the layout.
     * The insets are not consumed, so the AppBarLayout still receives them.
     */
    protected fun applySystemBarInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
    }
}
