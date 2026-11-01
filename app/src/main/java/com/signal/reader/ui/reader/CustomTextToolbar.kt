package com.signal.reader.ui.reader

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

class CustomTextToolbar(
    private val view: View,
    private val onShareQuote: (String) -> Unit
) : TextToolbar {

    private var actionMode: ActionMode? = null
    private var currentRect: Rect = Rect.Zero

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    companion object {
        private const val MENU_SHARE_QUOTE_ID = 10001
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        currentRect = rect

        val callback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                if (menu == null) return false

                // Add "Share quote" as the primary action
                menu.add(Menu.NONE, MENU_SHARE_QUOTE_ID, 0, "Share quote").apply {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }

                if (onCopyRequested != null) {
                    menu.add(Menu.NONE, android.R.id.copy, 1, android.R.string.copy).apply {
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    }
                }
                if (onSelectAllRequested != null) {
                    menu.add(Menu.NONE, android.R.id.selectAll, 2, android.R.string.selectAll).apply {
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    }
                }
                if (onCutRequested != null) {
                    menu.add(Menu.NONE, android.R.id.cut, 3, android.R.string.cut)
                }
                if (onPasteRequested != null) {
                    menu.add(Menu.NONE, android.R.id.paste, 4, android.R.string.paste)
                }

                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                return when (item?.itemId) {
                    MENU_SHARE_QUOTE_ID -> {
                        // Copy the selected text to clipboard through Compose's handler
                        onCopyRequested?.invoke()

                        // Retrieve the freshly copied text and launch the quote card sheet
                        val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clipText.isNullOrBlank()) {
                            onShareQuote(clipText)
                        }

                        mode?.finish()
                        true
                    }
                    android.R.id.copy -> {
                        onCopyRequested?.invoke()
                        mode?.finish()
                        true
                    }
                    android.R.id.selectAll -> {
                        onSelectAllRequested?.invoke()
                        true
                    }
                    android.R.id.cut -> {
                        onCutRequested?.invoke()
                        mode?.finish()
                        true
                    }
                    android.R.id.paste -> {
                        onPasteRequested?.invoke()
                        mode?.finish()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                actionMode = null
                status = TextToolbarStatus.Hidden
            }

            override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: AndroidRect?) {
                outRect?.set(
                    currentRect.left.toInt(),
                    currentRect.top.toInt(),
                    currentRect.right.toInt(),
                    currentRect.bottom.toInt()
                )
            }
        }

        if (actionMode == null) {
            status = TextToolbarStatus.Shown
            actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        } else {
            actionMode?.invalidateContentRect()
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }
}
