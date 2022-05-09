package com.goodwy.dialer.dialogs

import android.content.Intent
import android.os.SystemClock
import android.provider.CallLog
import android.text.SpannableString
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.KEY_PHONE
import com.goodwy.commons.helpers.PERMISSION_WRITE_CALL_LOG
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.adapters.RecentCallsAdapter
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.models.RecentCall
import kotlinx.android.synthetic.main.dialog_call_history.view.*
import kotlinx.android.synthetic.main.fragment_recents.*
import java.util.*

class ShowCallHistoryDialog(val activity: BaseSimpleActivity, var recentCalls: ArrayList<RecentCall>, callIds: ArrayList<Int>, call: RecentCall) {
    private var dialog: AlertDialog? = null
    private var view = activity.layoutInflater.inflate(R.layout.dialog_call_history, null)

    init {
        view.apply {
            RecentsHelper(activity).getRecentCalls(false) { allRecents ->
                val recents = allRecents.filter { callIds.contains(it.id) }.toMutableList() as ArrayList<RecentCall>
                activity.runOnUiThread {
                    RecentCallsAdapter(activity as SimpleActivity, recents, select_history_calls_list, null) {

                    }.apply {
                        select_history_calls_list.adapter = this
                    }
                }
            }

            SimpleContactsHelper(context).loadContactImage(call.photoUri, item_history_image, call.name)

            val nameToShow = SpannableString(call.name)
            item_history_name.apply {
                text = nameToShow
                setTextColor(context.config.textColor)
            }
            oneButton.apply {
                foreground.applyColorFilter(context.config.primaryColor)
                setOnClickListener {
                    activity.launchSendSMSIntent(call.phoneNumber)
                    dialog?.dismiss()
                }
            }
            twoButton.apply {
                foreground.applyColorFilter(context.config.primaryColor)
                setOnClickListener {
                    dialog?.dismiss()
                    if (context.config.showCallConfirmation) {
                        CallConfirmationDialog(activity as SimpleActivity, call.name) {
                            activity?.launchCallIntent(call.phoneNumber)
                        }
                    } else {
                        activity?.launchCallIntent(call.phoneNumber)
                    }
                }
            }
            threeButton.apply {
                foreground.applyColorFilter(context.config.primaryColor)
                setOnClickListener {
                    Intent().apply {
                        action = Intent.ACTION_INSERT_OR_EDIT
                        type = "vnd.android.cursor.item/contact"
                        putExtra(KEY_PHONE, call.phoneNumber)
                        activity.launchActivityIntent(this)
                    }
                    dialog?.dismiss()
                }
            }
            /*fourButton.apply {
                foreground.applyColorFilter(context.config.primaryColor)
                setOnClickListener {
                    activity.copyToClipboard(call.phoneNumber)
                    dialog?.dismiss()
                }
            }*/
            fourButton.apply {
                foreground.applyColorFilter(context.config.primaryColor)
                setOnClickListener {
                    ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
                        activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                            removeRecents(callIds)
                        }
                    }
                }
            }
            numberButton.apply {
                text = call.phoneNumber
                setTextColor(context.config.primaryColor)
                setOnClickListener {
                    activity.copyToClipboard(call.phoneNumber)
                    //dialog?.dismiss()
                }
            }
            blockButton.apply {
                val redColor = resources.getColor(R.color.red_missed)
                val blockedNumbers = activity.getBlockedNumbers()
                val color = if (activity.isNumberBlocked(call.phoneNumber, blockedNumbers)) { context.config.primaryColor } else { redColor }
                setTextColor(color)
                val blockText = if (activity.isNumberBlocked(call.phoneNumber, blockedNumbers)) {
                    resources.getString(R.string.unblock_number)
                } else {
                    resources.getString(R.string.block_number)
                }
                text = blockText
                setOnClickListener {
                    val blockedNumbers = activity.getBlockedNumbers()
                    val baseString = if (activity.isNumberBlocked(call.phoneNumber, blockedNumbers)) {
                        R.string.unblock_confirmation
                    } else { R.string.block_confirmation }
                    val question = String.format(resources.getString(baseString), call.phoneNumber)
                    ConfirmationDialog(activity, question) {
                        //blockNumbers(call.phoneNumber)
                        ensureBackgroundThread {
                            val blockedNumbers = activity.getBlockedNumbers()
                            if (activity.isNumberBlocked(call.phoneNumber, blockedNumbers)) {
                                activity.deleteBlockedNumber(call.phoneNumber)
                                text = resources.getString(R.string.block_number)
                                setTextColor(redColor)
                            } else {
                                activity.addBlockedNumber(call.phoneNumber)
                                text = resources.getString(R.string.unblock_number)
                                setTextColor(context.config.primaryColor)
                            }
                        }
                    }
                }
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setOnCancelListener { activity.recents_fragment?.refreshItems() }
            .setNegativeButton(R.string.cancel) { _, _ ->
                activity.recents_fragment?.refreshItems()
            }
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun blockNumbers(number: String) {
        ensureBackgroundThread {
            val blockedNumbers = activity.getBlockedNumbers()
            if (activity.isNumberBlocked(number, blockedNumbers)) {
                activity.deleteBlockedNumber(number)
            } else {
                activity.addBlockedNumber(number)
            }
            //dialog?.dismiss()
            //activity.recents_fragment?.refreshItems()
            //activity.finish()
            //activity.overridePendingTransition(0, 0) //TODO анимация активити
            //activity.launchActivityIntent(Intent(activity, MainActivity::class.java))
            //activity.overridePendingTransition(0, 0)
        }
    }

    private fun removeRecents(idsToRemove: ArrayList<Int>) {
        val callsToRemove = ArrayList<RecentCall>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.neighbourIDs.mapTo(idsToRemove, { it })
        }
        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            recentCalls.removeAll(callsToRemove)
            idsToRemove.removeAll(idsToRemove)
        }
        SystemClock.sleep(2000)
        activity.recents_fragment?.refreshItems()
    }
}
