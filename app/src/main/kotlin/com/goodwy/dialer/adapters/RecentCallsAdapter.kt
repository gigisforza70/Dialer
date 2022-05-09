package com.goodwy.dialer.adapters

import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.CallLog.Calls
import android.text.SpannableString
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.startActivity
import com.bumptech.glide.Glide
import com.goodwy.commons.activities.FAQActivity
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.CallHistoryActivity
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.dialogs.ShowCallHistoryDialog
import com.goodwy.dialer.dialogs.ShowGroupedCallsDialog
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.interfaces.RefreshItemsListener
import com.goodwy.dialer.models.RecentCall
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.item_call_history.view.*
import kotlinx.android.synthetic.main.item_recent_call.view.*
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_date_time
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_duration
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_frame
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_image
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_info
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_name
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_number
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_sim_id
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_sim_image
import kotlinx.android.synthetic.main.item_recent_call.view.item_recents_type
import java.util.*

class RecentCallsAdapter(
    activity: SimpleActivity, var recentCalls: ArrayList<RecentCall>, recyclerView: MyRecyclerView, val refreshItemsListener: RefreshItemsListener?,
    val showIcon: Boolean = false, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    private lateinit var outgoingCallText: String
    private lateinit var incomingCallText: String
    private lateinit var incomingMissedCallText: String
    private lateinit var infoIcon: Drawable
    private var fontSize = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val redColor = resources.getColor(R.color.red_missed) //md_red_700
    private var textToHighlight = ""

    init {
        initDrawables()
        initString()
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val selectedItems = getSelectedItems()
        val isOneItemSelected = selectedItems.size == 1
        val selectedNumber = "tel:${getSelectedPhoneNumber()}"

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected && showIcon
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected && showIcon
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && activity.config.getCustomSIM(selectedNumber) != "" && showIcon

            findItem(R.id.cab_block_number).isVisible = isNougatPlus() && showIcon
            findItem(R.id.cab_add_number).isVisible = isOneItemSelected && showIcon
            findItem(R.id.cab_send_sms).isVisible = showIcon
            findItem(R.id.cab_show_call_details).isVisible = isOneItemSelected && showIcon
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected && showIcon
            //findItem(R.id.cab_remove).isVisible = showIcon
            //findItem(R.id.cab_select_all).isVisible = showIcon
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_block_number -> askConfirmBlock()
            R.id.cab_add_number -> addNumberToContact()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_show_call_details -> showCallDetails()
            R.id.cab_copy_number -> copyNumber()
            R.id.cab_remove -> askConfirmRemove()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = recentCalls.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recentCalls.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recentCalls.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = if (showIcon) createViewHolder(R.layout.item_recent_call, parent) else createViewHolder(R.layout.item_call_history, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recentCall = recentCalls[position]
        holder.bindView(recentCall, refreshItemsListener != null, refreshItemsListener != null) { itemView, layoutPosition ->
            setupView(itemView, recentCall)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recentCalls.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.item_recents_image)
        }
    }

    fun initDrawables() {
        outgoingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_outgoing_call_vector, baseConfig.textColor)
        incomingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_incoming_call_vector, baseConfig.textColor)
        incomingMissedCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_missed_call_vector, baseConfig.textColor)
        infoIcon = resources.getColoredDrawableWithColor(R.drawable.ic_info_vector, baseConfig.primaryColor)
    }

    fun initString() {
        outgoingCallText = resources.getString(R.string.outgoing_call)
        incomingCallText = resources.getString(R.string.incoming_call)
        incomingMissedCallText = resources.getString(R.string.missed_call)
    }

    private fun callContact(useSimOne: Boolean) {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.callContactWithSim(phoneNumber, useSimOne)
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.config.removeCustomSIM("tel:$phoneNumber")
        finishActMode()
    }

    private fun askConfirmBlock() {
        val numbers = TextUtils.join(", ", getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), numbers)

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()
        val positions = getSelectedItemPositions()
        recentCalls.removeAll(callsToBlock)

        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                removeSelectedItems(positions)
                finishActMode()
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun sendSMS() {
        val numbers = getSelectedItems().map { it.phoneNumber }
        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntent(recipient)
    }

    private fun showCallDetails() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        val callIds = recentCall.neighbourIDs.map { it }.toMutableList() as ArrayList<Int>
        callIds.add(recentCall.id)
        ShowGroupedCallsDialog(activity, callIds)
    }

    private fun copyNumber() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(recentCall.phoneNumber)
        finishActMode()
    }

    private fun askConfirmRemove() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.neighbourIDs.mapTo(idsToRemove, { it })
        }

        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            recentCalls.removeAll(callsToRemove)
            activity.runOnUiThread {
                if (recentCalls.isEmpty()) {
                    refreshItemsListener?.refreshItems()
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    fun updateItems(newItems: ArrayList<RecentCall>, highlightText: String = "") {
        if (newItems.hashCode() != recentCalls.hashCode()) {
            recentCalls = newItems.clone() as ArrayList<RecentCall>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    fun getSelectedItems() = recentCalls.filter { selectedKeys.contains(it.id) } as ArrayList<RecentCall>

    private fun getLastItem() = recentCalls.last()

    private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumber

    private fun setupView(view: View, call: RecentCall) {
        view.apply {
            divider?.setBackgroundColor(textColor)
            if (getLastItem() == call || !context.config.useDividers) divider?.visibility = View.INVISIBLE else divider?.visibility = View.VISIBLE

            item_recents_frame.isSelected = selectedKeys.contains(call.id)
            var nameToShow = SpannableString(call.name)
            if (nameToShow[0].toString() == "+") nameToShow = SpannableString(getPhoneNumberNormalizer(activity, number = nameToShow.toString()))
            if (call.neighbourIDs.isNotEmpty()) {
                nameToShow = SpannableString("$nameToShow (${call.neighbourIDs.size + 1})")
            }

            if (textToHighlight.isNotEmpty() && nameToShow.contains(textToHighlight, true)) {
                nameToShow = SpannableString(nameToShow.toString().highlightTextPart(textToHighlight, adjustedPrimaryColor))
            }

            item_recents_name.apply {
                text = nameToShow
                setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor) //(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }

            item_recents_number.apply {
                beVisibleIf(call.phoneNumber != call.name && showIcon)
                val phoneNumberNormalizer = getPhoneNumberNormalizer(activity, number = call.phoneNumber)
                val phoneNumber = if (call.phoneNumber[0].toString() == "+") phoneNumberNormalizer else call.phoneNumber
                text = if (call.phoneNumberType != null && call.phoneNumberLabel != null) activity.getPhoneNumberTypeText(call.phoneNumberType!!, call.phoneNumberLabel!!) else phoneNumber
                //setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            item_recents_date_time.apply {
                text = call.startTS.formatDateOrTime(context, refreshItemsListener != null, false)
                //setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            item_recents_duration.apply {
                text = call.duration.getFormattedDuration()
                setTextColor(textColor)
                beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            item_recents_sim_image.beVisibleIf(areMultipleSIMsAvailable)
            item_recents_sim_id.beVisibleIf(areMultipleSIMsAvailable)
            if (areMultipleSIMsAvailable) {
                item_recents_sim_image.applyColorFilter(textColor)
                item_recents_sim_id.setTextColor(textColor.getContrastColor())
                item_recents_sim_id.text = call.simID.toString()
            }

            if (call.phoneNumber == call.name) {
                SimpleContactsHelper(context).loadContactImage(call.photoUri, item_recents_image, call.name, letter = false)
                item_recents_image_icon?.beVisible()
            } else {
                SimpleContactsHelper(context).loadContactImage(call.photoUri, item_recents_image, call.name)
                item_recents_image_icon?.beGone()
            }

            val drawable = when (call.type) {
                Calls.OUTGOING_TYPE -> outgoingCallIcon
                Calls.MISSED_TYPE -> incomingMissedCallIcon
                else -> incomingCallIcon
            }
            item_recents_type.setImageDrawable(drawable)

            val type = when (call.type) {
                Calls.OUTGOING_TYPE -> outgoingCallText
                Calls.MISSED_TYPE -> incomingMissedCallText
                else -> incomingCallText
            }
            item_recents_type_name?.apply {
                text = type
                setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            item_recents_info.apply {
                beVisibleIf(showIcon)
                applyColorFilter(context.config.primaryColor)
                setImageDrawable(infoIcon)
                setOnClickListener {
                    showCallHistory(call)
                }
            }
        }
    }

    private fun getCallList(call: RecentCall) = recentCalls.filter { it.phoneNumber == call.phoneNumber}.toMutableList() as ArrayList<RecentCall>

    private fun showCallHistory(call: RecentCall) {
        val callIdList : ArrayList<Int> = arrayListOf()
        for (i in getCallList(call)){ callIdList.add(i.id) } // добавляем все отдельные записи
        for (n in getCallList(call)){ callIdList.addAll(n.neighbourIDs) } // добавляем все сгруппированные записи
        //ShowCallHistoryDialog(activity, recentCalls, callIdList, call)
        //activity.launchActivityIntent(Intent(activity, CallHistoryActivity::class.java))
        Intent(activity, CallHistoryActivity::class.java).apply {
            putExtra(CURRENT_PHONE_NUMBER, call.phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun confirmRemove(call: RecentCall) {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecent(call)
            }
        }
    }

    private fun removeRecent(call: RecentCall) {
       /*if (selectedKeys.isEmpty()) {
            return
        }*/

        val callsToRemove = ArrayList<RecentCall>()
        callsToRemove.add(call)
        val positions = ArrayList<Int>(0)
        val idsToRemove = ArrayList<Int>()
        idsToRemove.add(call.id)
        /*callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.neighbourIDs.mapTo(idsToRemove, { it })
        }*/

        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            recentCalls.removeAll(callsToRemove)
            (activity as CallHistoryActivity).refreshItems()
            /*activity.runOnUiThread {
                removeSelectedItems(idsToRemove)
                refreshItemsListener?.refreshItems()
                (activity as CallHistoryActivity).refreshItems()
                finishActMode()
            }*/
        }
    }


    /*private fun viewContactInfo(contact: SimpleContact) {
        activity.startContactDetailsIntent(contact)
    }*/
}
