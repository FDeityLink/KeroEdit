/*
 * TODO:
 * abstract fun rename()?
 * Extend PoppableTab
 * Store undo pointer to call markUnchanged() on undo()/redo() if the same state as when save() was called is met
 * Create abstract class representing a savable object and use that rather than Path and delegating save() to subclasses?
 * Handle tabs that have a '*' in their name not as a marker of changes (is this already handled?)
 */

package io.fdeitylink.util.fx

import java.util.ArrayDeque

import java.nio.file.Path

import javafx.scene.Node

import javafx.event.Event
import javafx.event.EventHandler

import javafx.scene.control.ButtonType

import javafx.scene.control.Tab
import javafx.scene.control.Tooltip

import io.fdeitylink.keroedit.Messages

//TODO: Get rid of @JvmOverloads annotation once I convert ScriptEditTab, MapEditTab, and HackTab?
abstract class FileEditTab
@JvmOverloads protected constructor(p: Path, text: String? = null, content: Node? = null): Tab(text, content) {
    //https://xkcd.com/853/
    private val undoQueue = ArrayDeque<UndoableEdit>()
    private val redoQueue = ArrayDeque<UndoableEdit>()

    val path: Path = p.toAbsolutePath()

    /*
     * Changes are done via markChanged() and markUnchanged().
     * This is so that subclasses can increase the visibility
     * of the setter methods as needed. If the visibility of only
     * one method needs to be changed, that can be done so other
     * classes cannot arbitrarily change the boolean to true or false.
     */
    var isChanged = false
        private set

    init {
        id = path.toString()
        tooltip = Tooltip(path.toString())

        onCloseRequest = EventHandler<Event> { event ->
            if (isChanged) {
                val alert = FXUtil.createAlert(title = this.text?.substring(0, this.text.lastIndexOf('*')),
                                               message = Messages.getString("FileEditTab.UNSAVED_CHANGES"))

                alert.buttonTypes.addAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
                alert.showAndWait().ifPresent {
                    if (ButtonType.YES == it) {
                        save()
                    }
                    else if (ButtonType.CANCEL == it) {
                        event?.consume()
                    }
                }
            }
        }
    }

    open fun undo() {
        if (!undoQueue.isEmpty()) {
            markChanged()
            val edit = undoQueue.removeFirst()
            redoQueue.addFirst(edit)
            edit.undo()
        }
    }

    open fun redo() {
        if (!redoQueue.isEmpty()) {
            markChanged()
            val edit = redoQueue.removeFirst()
            undoQueue.addFirst(edit)
            edit.redo()
        }
    }

    abstract fun save()

    protected open fun markChanged() {
        if (!isChanged) {
            isChanged = true
            if (!text.endsWith("*")) {
                text += '*'
            }
        }
    }

    protected open fun markUnchanged() {
        if (isChanged) {
            isChanged = false
            if (text.endsWith("*")) {
                text = text.substring(0, text.lastIndexOf("*"))
            }
        }
    }

    protected fun addUndo(edit: UndoableEdit) {
        markChanged()
        redoQueue.clear()
        undoQueue.addFirst(edit)
    }
}