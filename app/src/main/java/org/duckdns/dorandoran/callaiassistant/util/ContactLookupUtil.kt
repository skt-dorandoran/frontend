package org.duckdns.dorandoran.callaiassistant.util

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ContactLookupUtil {
    data class DisplayInfo(
        val primary: String,
        val secondary: String
    )

    suspend fun resolveDisplayInfo(context: Context, rawNumber: String): DisplayInfo = withContext(Dispatchers.IO) {
        val formattedNumber = formatPhoneNumber(rawNumber)
        val fallback = formattedNumber.ifBlank { "상대방" }
        val contactName = getContactName(context, rawNumber)
        if (contactName.isNullOrBlank()) {
            DisplayInfo(primary = fallback, secondary = "")
        } else {
            DisplayInfo(primary = contactName, secondary = formattedNumber)
        }
    }

    private fun getContactName(context: Context, rawNumber: String): String? {
        if (rawNumber.isBlank()) return null
        val hasContactPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactPermission) return null

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(rawNumber)
            .build()
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    private fun formatPhoneNumber(raw: String): String {
        return formatPhoneNumberByRule(raw)
    }
}

@Composable
fun rememberContactsVersion(context: Context): Long {
    var version by remember { mutableLongStateOf(0L) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                version = System.currentTimeMillis()
            }
        }
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer
        )
        context.contentResolver.registerContentObserver(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            true,
            observer
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return version
}
