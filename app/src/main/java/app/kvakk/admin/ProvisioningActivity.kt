package app.kvakk.admin

import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

/**
 * Answers the setup wizard during QR-code provisioning (required on Android 12+):
 * asks for a fully managed device, then applies the kiosk policies.
 */
@SuppressLint("InlinedApi") // Only launched by Android 10+ setup, which has these constants.
class ProvisioningActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            DevicePolicyManager.ACTION_GET_PROVISIONING_MODE -> setResult(
                RESULT_OK,
                Intent().putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                ),
            )

            DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE -> {
                KioskPolicy.apply(this)
                setResult(RESULT_OK)
            }

            else -> setResult(RESULT_CANCELED)
        }
        finish()
    }
}
