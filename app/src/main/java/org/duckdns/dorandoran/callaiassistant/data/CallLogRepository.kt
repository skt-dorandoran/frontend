package org.duckdns.dorandoran.callaiassistant.data

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

class CallLogRepository(private val context: Context) {

    suspend fun getCallHistory(limit: Int = 100): List<CallLogItem> = withContext(Dispatchers.IO) {
        val calls = mutableListOf<CallLogItem>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME
        )

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)

            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idIndex)
                val number = cursor.getString(numberIndex) ?: ""
                val type = when (cursor.getInt(typeIndex)) {
                    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                    else -> CallType.OUTGOING
                }
                val date = Date(cursor.getLong(dateIndex))
                val duration = cursor.getLong(durationIndex)
                val cachedName = cursor.getString(nameIndex)

                val contactName = cachedName?.takeIf { it.isNotBlank() }
                    ?: getContactNameSync(number)

                calls.add(
                    CallLogItem(
                        id = id,
                        phoneNumber = number,
                        contactName = contactName,
                        callType = type,
                        date = date,
                        duration = duration
                    )
                )
                count++
            }
        }
        calls
    }

    suspend fun getContactName(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        getContactNameSync(phoneNumber)
    }

    private fun getContactNameSync(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(phoneNumber)
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
}
