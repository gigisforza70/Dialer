package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.viewpager.widget.ViewPager
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ViewPagerAdapter
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.helpers.OPEN_DIAL_PAD_AT_LAUNCH
import com.goodwy.dialer.fragments.MyViewPagerFragment
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.helpers.tabsList
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_contacts.*
import kotlinx.android.synthetic.main.fragment_favorites.*
import kotlinx.android.synthetic.main.fragment_recents.*
import java.util.*

class MainActivity : SimpleActivity() {
    private var launchedDialer = false
    private var isSearchOpen = false
    private var searchMenuItem: MenuItem? = null
    private var storedShowTabs = 0

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupTabColors()

        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false

        if (isDefaultDialer()) {
            checkContactPermissions()
        } else {
            launchSetDefaultDialerIntent()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, launchedDialer)
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        val adjustedPrimaryColor = getAdjustedPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, adjustedPrimaryColor.getContrastColor())
        main_dialpad_button.apply {
            setImageDrawable(dialpadIcon)
            background.applyColorFilter(adjustedPrimaryColor)
        }

        //main_tabs_holder.setBackgroundColor(config.backgroundColor)
        main_tabs_holder.setSelectedTabIndicatorColor(baseConfig.backgroundColor)

        if (viewpager.adapter != null) {

            if (config.useIconTabs) {
                main_tabs_holder.getTabAt(0)?.setIcon(R.drawable.ic_star_vector)
                main_tabs_holder.getTabAt(0)?.text = null
                main_tabs_holder.getTabAt(1)?.setIcon(R.drawable.ic_clock)
                main_tabs_holder.getTabAt(1)?.text = null
                main_tabs_holder.getTabAt(2)?.setIcon(R.drawable.ic_person_rounded)
                main_tabs_holder.getTabAt(2)?.text = null
            } else {
                main_tabs_holder.getTabAt(0)?.icon = null
                main_tabs_holder.getTabAt(0)?.setText(R.string.favorites_tab)
                main_tabs_holder.getTabAt(1)?.icon = null
                main_tabs_holder.getTabAt(1)?.setText(R.string.recents)
                main_tabs_holder.getTabAt(2)?.icon = null
                main_tabs_holder.getTabAt(2)?.setText(R.string.contacts_tab)
            }

            getInactiveTabIndexes(viewpager.currentItem).forEach {
                main_tabs_holder.getTabAt(it)?.icon?.applyColorFilter(config.textColor)
                main_tabs_holder.getTabAt(it)?.icon?.alpha = 220 // max 255
                main_tabs_holder.setTabTextColors(config.textColor,
                    config.primaryColor)
            }

            main_tabs_holder.getTabAt(viewpager.currentItem)?.icon?.applyColorFilter(adjustedPrimaryColor)
            main_tabs_holder.getTabAt(viewpager.currentItem)?.icon?.alpha = 220 // max 255
            getAllFragments().forEach {
                it?.setupColors(config.textColor, config.primaryColor, getAdjustedPrimaryColor())
                main_tabs_holder.setTabTextColors(config.textColor,
                    config.primaryColor)
            }
        }

        if (!isSearchOpen) {
            if (storedShowTabs != config.showTabs) {
                hideTabs()
            }
            refreshItems(true)
        }

        checkShortcuts()
        Handler().postDelayed({
            recents_fragment?.refreshItems()
        }, 2000)
        invalidateOptionsMenu()
    }

    @SuppressLint("MissingSuperCall")
    override fun onDestroy() {
        super.onDestroy()
        storedShowTabs = config.showTabs
        config.lastUsedViewPagerPage = viewpager.currentItem
    }

    override fun onPause() {
        super.onPause()
        storedShowTabs = config.showTabs
        config.lastUsedViewPagerPage = viewpager.currentItem
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.apply {
            findItem(R.id.clear_call_history).isVisible = getCurrentFragment() == recents_fragment
            findItem(R.id.add_contact).isVisible = getCurrentFragment() == contacts_fragment

            setupSearch(this)
            updateMenuItemColors(this)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add_contact -> launchCreateNewIntent()
            R.id.clear_call_history -> clearCallHistory()
            R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we dont really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            initFragments()
        }
    }

    private fun setupSearch(menu: Menu) {
        updateMenuItemColors(menu)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(R.string.search)
            //setBackgroundColor(config.textColor)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        getCurrentFragment()?.onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                isSearchOpen = true
                main_dialpad_button.beGone()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                getCurrentFragment()?.onSearchClosed()
                isSearchOpen = false
                main_dialpad_button.beVisible()
                return true
            }
        })
    }

    private fun clearCallHistory() {
        ConfirmationDialog(this, "", R.string.clear_history_confirmation) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    recents_fragment?.refreshItems()
                }
            }
        }
    }

    private fun launchCreateNewIntent() {
        Intent().apply {
            action = Intent.ACTION_INSERT
            data = ContactsContract.Contacts.CONTENT_URI
            launchActivityIntent(this)
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = resources.getDrawable(R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun setupTabColors() {
        val lastUsedPage = getDefaultTab()
        main_tabs_holder.apply {
            //background = ColorDrawable(config.backgroundColor)
            setSelectedTabIndicatorColor(baseConfig.backgroundColor)
            getTabAt(lastUsedPage)?.select()
            getTabAt(lastUsedPage)?.icon?.applyColorFilter(getAdjustedPrimaryColor())
            getTabAt(lastUsedPage)?.icon?.alpha = 220 // max 255

            getInactiveTabIndexes(lastUsedPage).forEach {
                getTabAt(it)?.icon?.applyColorFilter(config.textColor)
                getTabAt(it)?.icon?.alpha = 220 // max 255
            }
        }

        main_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                it.icon?.applyColorFilter(config.textColor)
                it.icon?.alpha = 220 // max 255
            },
            tabSelectedAction = {
                viewpager.currentItem = it.position
                it.icon?.applyColorFilter(getAdjustedPrimaryColor())
                it.icon?.alpha = 220 // max 255

            }
        )
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until tabsList.size).filter { it != activeIndex }

    private fun initFragments() {
        viewpager.offscreenPageLimit = 2
        viewpager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                searchMenuItem?.collapseActionView()
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                main_tabs_holder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                invalidateOptionsMenu()
            }
        })

        if (!config.tabsСhanged) hideTabs()

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        main_tabs_holder.onGlobalLayout {
            Handler().postDelayed({
                var wantedTab = getDefaultTab()

                // open the Recents tab if we got here by clicking a missed call notification
                if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                    wantedTab = main_tabs_holder.tabCount - 1
                }

                main_tabs_holder.getTabAt(wantedTab)?.select()
                invalidateOptionsMenu()
            }, 100L)
        }

        main_dialpad_button.setOnClickListener {
            launchDialpad()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun hideTabs() {
        val selectedTabIndex = main_tabs_holder.selectedTabPosition
        viewpager.adapter = null
        main_tabs_holder.removeAllTabs()
        var skippedTabs = 0
        var isAnySelected = false
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value == 0) {
                skippedTabs++
            } else {
                val tab = if (config.useIconTabs) main_tabs_holder.newTab().setIcon(getTabIcon(index)) else main_tabs_holder.newTab().setText(getTabText(index))
                tab.contentDescription = getTabContentDescription(index)
                val wasAlreadySelected = selectedTabIndex > -1 && selectedTabIndex == index - skippedTabs
                val shouldSelect = !isAnySelected && wasAlreadySelected
                if (shouldSelect) {
                    isAnySelected = true
                }
                main_tabs_holder.addTab(tab, index - skippedTabs, shouldSelect)
                main_tabs_holder.setTabTextColors(config.textColor,
                    config.primaryColor)
            }
        }
        if (!isAnySelected) {
            main_tabs_holder.selectTab(main_tabs_holder.getTabAt(getDefaultTab()))
        }
        main_tabs_background.beGoneIf(main_tabs_holder.tabCount == 1)
        main_tabs_holder.beGoneIf(main_tabs_holder.tabCount == 1)
        storedShowTabs = config.showTabs
    }

    private fun getTabText(position: Int): Int {
        val drawableId = when (position) {
            0 -> R.string.favorites_tab
            1 -> R.string.recents
            else -> R.string.contacts_tab
        }
        return drawableId
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_star_vector
            1 -> R.drawable.ic_clock
            else -> R.drawable.ic_person_rounded
        }
        return resources.getColoredDrawableWithColor(drawableId, config.textColor)
    }

    private fun getTabContentDescription(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.favorites_tab
            1 -> R.string.call_history_tab
            else -> R.string.contacts_tab
        }

        return resources.getString(stringId)
    }

    private fun refreshItems(openLastTab: Boolean = false) {
        if (isDestroyed || isFinishing) {
            return
        }

        if (viewpager.adapter == null) {
            viewpager.adapter = ViewPagerAdapter(this)
            viewpager.currentItem = if (openLastTab) main_tabs_holder.selectedTabPosition else getDefaultTab()
            viewpager.onGlobalLayout {
                refreshFragments()
            }
        } else {
            refreshFragments()
        }
    }

    private fun launchDialpad() {
        Intent(applicationContext, DialpadActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun refreshFragments() {
        favorites_fragment?.refreshItems()
        recents_fragment?.refreshItems()
        contacts_fragment?.refreshItems()
    }

    //private fun getAllFragments() = arrayListOf(favorites_fragment, recents_fragment, contacts_fragment).toMutableList() as ArrayList<MyViewPagerFragment?>
    private fun getAllFragments(): ArrayList<MyViewPagerFragment> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment>()

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(favorites_fragment)
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(recents_fragment)
        }

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(contacts_fragment)
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment? = getAllFragments().getOrNull(viewpager.currentItem)

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        return when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < main_tabs_holder.tabCount) config.lastUsedViewPagerPage else 0
            TAB_FAVORITES -> 0
            TAB_CALL_HISTORY -> if (showTabsMask and TAB_FAVORITES > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_CONTACTS > 0) {
                    if (showTabsMask and TAB_FAVORITES > 0) {
                        if (showTabsMask and TAB_CALL_HISTORY > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_CALL_HISTORY > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
            FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons),
            FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }
}
