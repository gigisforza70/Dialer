package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.view.Menu
import androidx.appcompat.content.res.AppCompatResources
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.SimpleContact
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.RecentCallsAdapter
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getPhoneNumberTypeText
import com.goodwy.dialer.extensions.startContactDetailsIntent
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.interfaces.RefreshItemsListener
import com.goodwy.dialer.models.RecentCall
import kotlinx.android.synthetic.main.activity_call_history.*
import kotlinx.android.synthetic.main.activity_call_history.call_history_holder
import kotlinx.android.synthetic.main.fragment_recents.view.*
import kotlin.collections.ArrayList

class CallHistoryActivity : SimpleActivity(), RefreshItemsListener {
    private var allContacts = ArrayList<SimpleContact>()
    private var allRecentCall = ArrayList<RecentCall>()
    private var privateCursor: Cursor? = null
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFFEBEBEB.toInt()

    private fun getCurrentPhoneNumber() = intent.getStringExtra(CURRENT_PHONE_NUMBER) ?: ""
    private fun isInternationalNumber() = getCurrentPhoneNumber()[0].toString() == "+"

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_history)
        //SimpleContactsHelper(this).getAvailableContacts(false) { gotContacts(it) }
        //SimpleContactsHelper(this).loadContactImage(getCall().photoUri, item_history_image, getCall().name)
        oneButton.foreground.applyColorFilter(config.primaryColor)
        twoButton.foreground.applyColorFilter(config.primaryColor)
        threeButton.foreground.applyColorFilter(config.primaryColor)
        fourButton.foreground.applyColorFilter(config.primaryColor)

        val phoneNumberNormalizer = getPhoneNumberNormalizer(this, number = getCurrentPhoneNumber())
        val phoneNumber = if (isInternationalNumber()) phoneNumberNormalizer else getCurrentPhoneNumber()

    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        call_history_placeholder_container.beGone()
        updateTextColors(call_history_holder)
        updateBackgroundColors()
        refreshItems()
    }

    fun updateBackgroundColors(color: Int = baseConfig.backgroundColor) {
        val whiteButton = AppCompatResources.getDrawable(this, R.drawable.call_history_button_white)//resources.getColoredDrawableWithColor(R.drawable.call_history_button_white, white)
        val whiteBackgroundHistory = AppCompatResources.getDrawable(this, R.drawable.call_history_background_white)//resources.getColoredDrawableWithColor(R.drawable.call_history_background_white, white)
        val red = resources.getColor(R.color.red_missed)

        val phoneNumberNormalizer = getPhoneNumberNormalizer(this, number = getCurrentPhoneNumber())
        val phoneNumber = if (isInternationalNumber()) phoneNumberNormalizer else getCurrentPhoneNumber()

        call_history_number_type.beGone()
        call_history_number_type.setTextColor(config.textColor)
        call_history_number_container.setOnClickListener {
            copyToClipboard(getCurrentPhoneNumber())
        }
        call_history_number.text = phoneNumber
        call_history_number.setTextColor(config.primaryColor)

        if (baseConfig.backgroundColor == white) {
            supportActionBar?.setBackgroundDrawable(ColorDrawable(0xFFf2f2f6.toInt()))
            window.decorView.setBackgroundColor(0xFFf2f2f6.toInt())
            window.statusBarColor = 0xFFf2f2f6.toInt()
            window.navigationBarColor = 0xFFf2f2f6.toInt()
        } else window.decorView.setBackgroundColor(color)
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            call_history_placeholder_container.background = whiteButton
            oneButton.background = whiteButton
            twoButton.background = whiteButton
            threeButton.background = whiteButton
            fourButton.background = whiteButton
            call_history_list.background = whiteBackgroundHistory
            call_history_number_container.background = whiteButton
            call_history_birthdays_container.background = whiteButton
            val blockcolor = if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers())) { config.primaryColor } else { red }
            blockButton.setTextColor(blockcolor)
            val blockText = if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers()))
                { resources.getString(R.string.unblock_number) } else { resources.getString(R.string.block_number)}
            blockButton.text = blockText
            blockButton.background = whiteButton
        } else window.decorView.setBackgroundColor(color)
    }

    fun updateBackgroundHistory(color: Int = baseConfig.backgroundColor) {
        val whiteBackgroundHistory = resources.getColoredDrawableWithColor(R.drawable.call_history_background_white, white)
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            call_history_list.background = whiteBackgroundHistory
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
       // menuInflater.inflate(R.menu.menu_dialpad, menu)
        updateMenuItemColors(menu)
        return true
    }

    /*override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add_number_to_contact -> addNumberToContact()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun copyNumber() {
        val сlip = dialpad_input.value
        copyToClipboard(сlip)
    }*/

    private fun checkDialIntent(): Boolean {
        return if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) && intent.data != null && intent.dataString?.contains("tel:") == true) {
            val number = Uri.decode(intent.dataString).substringAfter("tel:")
           // dialpad_input.setText(number)
           // dialpad_input.setSelection(number.length)
            true
        } else {
            false
        }
    }

    /*private fun addNumberToContact() {
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, dialpad_input.value)
            launchActivityIntent(this)
        }
    }

    private fun dialpadPressed(char: Char, view: View?) {
        dialpad_input.addCharacter(char)
        view?.performHapticFeedback()
    }

    private fun clearChar(view: View) {
        dialpad_input.dispatchKeyEvent(dialpad_input.getKeyEvent(KeyEvent.KEYCODE_DEL))
        view.performHapticFeedback()
    }

    private fun clearInput() {
        dialpad_input.setText("")
    }

    private fun disableKeyboardPopping() {
        dialpad_input.showSoftInputOnFocus = false
    }*/

    /*private fun gotContacts(newContacts: ArrayList<SimpleContact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        runOnUiThread {
            //if (!checkDialIntent() && dialpad_input.value.isEmpty()) {
            refreshItems()
           // }
        }
    }*/

    override fun refreshItems() {
        val privateCursor = this.getMyContactsCursor(false, true)?.loadInBackground()
        val groupSubsequentCalls = false // группировать звонки?   this.config.groupSubsequentCalls ?: false
        RecentsHelper(this).getRecentCalls(groupSubsequentCalls) { recents ->
            SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
                val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

                recents.filter { it.phoneNumber == it.name }.forEach { recent ->
                    var wasNameFilled = false
                    if (privateContacts.isNotEmpty()) {
                        val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
                        if (privateContact != null) {
                            recent.name = privateContact.name
                            wasNameFilled = true
                        }
                    }

                    if (!wasNameFilled) {
                        val contact = contacts.firstOrNull { it.phoneNumbers.first() == recent.phoneNumber }
                        if (contact != null) {
                            recent.name = contact.name
                        }
                    }
                }

                allContacts = contacts
                allRecentCall = recents
                this.runOnUiThread {
                    if (recents.isEmpty()) {
                        call_history_list.beGone()
                        call_history_placeholder_container.beVisible()
                    } else {
                        call_history_list.beVisible()
                        call_history_placeholder_container.beGone()
                        gotRecents(recents)
                        updateBackgroundColors()
                        updateBackgroundHistory()
                        updateButton()
                    }
                }
            }
        }
    }

    private fun gotRecents(recents: ArrayList<RecentCall>) {
        if (recents.isEmpty()) {
            call_history_list.beGone()
            call_history_placeholder_container.beVisible()
        } else {
            call_history_list.beVisible()
            call_history_placeholder_container.beGone()

            val currAdapter = call_history_list.adapter
            val recents = allRecentCall.filter { it.phoneNumber == getCurrentPhoneNumber()}.toMutableList() as java.util.ArrayList<RecentCall>
            if (currAdapter == null) {
                RecentCallsAdapter(this as SimpleActivity, recents, call_history_list, this) {
                    /*val recentCall = it as RecentCall
                    if (this.config.showCallConfirmation) {
                        CallConfirmationDialog(this as SimpleActivity, recentCall.name) {
                            this.launchCallIntent(recentCall.phoneNumber)
                        }
                    } else {
                        this.launchCallIntent(recentCall.phoneNumber)
                    }*/
                }.apply {
                    call_history_list.adapter = this
                }

                if (this.areSystemAnimationsEnabled) {
                    call_history_list.scheduleLayoutAnimation()
                }
                updateBackgroundColors()
            } else {
                (currAdapter as RecentCallsAdapter).updateItems(recents)
            }
        }
    }

    private fun updateButton() {
        val call: RecentCall? = getCallList().firstOrNull()
        if (call != null) {

            val contact = getContactList()
            if (contact != null) {
                threeButton.apply {
                    foreground = resources.getColoredDrawableWithColor(R.drawable.ic_contacts, config.primaryColor)
                    setOnClickListener {
                        viewContactInfo(contact)
                    }
                }
                if (contact.birthdays.firstOrNull() != null) {
                    val monthName = getDateFormatFromDateString(this@CallHistoryActivity, contact.birthdays.first(), "yyyy-MM-dd")
                    call_history_birthdays_container.apply {
                        beVisible()
                        setOnClickListener {
                            copyToClipboard(monthName!!)
                        }
                    }
                    call_history_birthdays_title.apply {
                        setTextColor(config.textColor)
                    }
                    call_history_birthdays.apply {
                        text = monthName
                        setTextColor(config.primaryColor)
                    }
                }

                if (contact.phoneNumbersInfo.filter { it.normalizedNumber == getCurrentPhoneNumber() }.firstOrNull() != null) {
                    call_history_number_type_container.beVisible()
                    call_history_number_type.apply {
                        beVisible()
                        //text = contact.phoneNumbersInfo.filter { it.normalizedNumber == getCurrentPhoneNumber()}.toString()
                        val phoneNumberType = contact.phoneNumbersInfo.filter { it.normalizedNumber == getCurrentPhoneNumber() }.first().type
                        val phoneNumberLabel = contact.phoneNumbersInfo.filter { it.normalizedNumber == getCurrentPhoneNumber() }.first().label
                        text = getPhoneNumberTypeText(phoneNumberType, phoneNumberLabel)
                    }
                    call_history_favorite_icon.apply {
                        beVisibleIf(contact.phoneNumbersInfo.filter { it.normalizedNumber == getCurrentPhoneNumber() }.first().favorite == "1")
                        applyColorFilter(config.textColor)
                    }
                }
            } else {
                threeButton.apply {
                    foreground = resources.getColoredDrawableWithColor(R.drawable.ic_add_person_vector, config.primaryColor)
                    setOnClickListener {
                        Intent().apply {
                            action = Intent.ACTION_INSERT_OR_EDIT
                            type = "vnd.android.cursor.item/contact"
                            putExtra(KEY_PHONE, getCurrentPhoneNumber())
                            launchActivityIntent(this)
                        }
                    }
                }
            }

            if (call.phoneNumber == call.name) {
                SimpleContactsHelper(this).loadContactImage(call.photoUri, call_history_image, call.name, letter = false)
                call_history_image_icon.beVisible()
            } else {
                SimpleContactsHelper(this).loadContactImage(call.photoUri, call_history_image, call.name)
                call_history_image_icon.beGone()
            }

            call_history_placeholder_container.beGone()

            val nameToShow = SpannableString(call.name)

            call_history_name.apply {
                text = nameToShow
                setTextColor(context.config.textColor)
                setOnClickListener {
                    copyToClipboard(call.name)
                }
            }

            oneButton.apply {
                setOnClickListener {
                    launchSendSMSIntent(call.phoneNumber)
                }
            }

            twoButton.apply {
                setOnClickListener {
                    makeСall(call)
                }
            }

            fourButton.apply {
                setOnClickListener {
                    launchShare()
                }
            }

            blockButton.apply {
                setOnClickListener {
                    askConfirmBlock()
                }
            }
        } else {
            call_history_list.beGone()
            call_history_placeholder_container.beVisible()
        }
    }

    private fun getItemCount() = getSelectedItems().size

    private fun getSelectedItems() = allRecentCall.filter { getCurrentPhoneNumber().contains(it.phoneNumber) } as java.util.ArrayList<RecentCall>

    private fun getCallList() = allRecentCall.filter { it.phoneNumber == getCurrentPhoneNumber()}.toMutableList() as java.util.ArrayList<RecentCall>

    private fun getContactList() = allContacts.firstOrNull { it.doesContainPhoneNumber(getCurrentPhoneNumber()) }

    private fun makeСall(call: RecentCall) {
        if (config.showCallConfirmation) {
            CallConfirmationDialog(this as SimpleActivity, call.name) {
                launchCallIntent(call.phoneNumber)
            }
        } else {
            launchCallIntent(call.phoneNumber)
        }
    }

    private fun askConfirmBlock() {
        val baseString = if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers())) {
            R.string.unblock_confirmation
        } else { R.string.block_confirmation }
        val question = String.format(resources.getString(baseString), getCurrentPhoneNumber())

        ConfirmationDialog(this, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        val red = resources.getColor(R.color.red_missed)
        //ensureBackgroundThread {
        runOnUiThread {
            if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers())) {
                deleteBlockedNumber(getCurrentPhoneNumber())
                blockButton.text = getString(R.string.block_number)
                blockButton.setTextColor(red)
            } else {
                addBlockedNumber(getCurrentPhoneNumber())
                blockButton.text = getString(R.string.unblock_number)
                blockButton.setTextColor(config.primaryColor)
            }
        }
    }

    private fun askConfirmRemove() {
        val message = if ((call_history_list?.adapter as? RecentCallsAdapter)?.getSelectedItems()!!.isEmpty()) {
            getString(R.string.clear_history_confirmation)
        } else getString(R.string.remove_confirmation)
        ConfirmationDialog(this, message) {
            handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (getCurrentPhoneNumber().isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        //val positions = getSelectedItemPositions()
        val idsToRemove = java.util.ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.neighbourIDs.mapTo(idsToRemove, { it })
        }

        RecentsHelper(this).removeRecentCalls(idsToRemove) {
            //allRecentCall.removeAll(callsToRemove)
            runOnUiThread {
                if (allRecentCall.isEmpty()) {
                    refreshItems()
                    finishActMode()
                } else {
                    refreshItems()//(call_history_list?.adapter as? RecentCallsAdapter)?.removePositions(positions)
                }
            }
        }
    }

    private fun finishActMode() {
        (call_history_list?.adapter as? RecentCallsAdapter)?.finishActMode()
    }

    private fun viewContactInfo(contact: SimpleContact) {
        this.startContactDetailsIntent(contact)
    }

    private fun launchShare() {
        val text = getCurrentPhoneNumber()
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, getCurrentPhoneNumber())
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            startActivity(Intent.createChooser(this, getString(R.string.invite_via)))
        }
    }
}
