/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.menurules;

import static com.google.android.accessibility.talkback.Feedback.EditText.Action.COPY;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CURSOR_TO_BEGINNING;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CURSOR_TO_END;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CUT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.END_SELECT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.PASTE;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.SELECT_ALL;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.START_SELECT;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_ITEM_UNKNOWN;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_TYPE_CUSTOM_ACTION;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_TYPE_EDIT_OPTIONS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.AbstractOnContextMenuItemClickListener;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;

/** Adds custom actions to the talkback context menu. */
public class RuleCustomAction extends NodeMenuRule {
  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  TalkBackAnalytics analytics;

  public RuleCustomAction(
      Pipeline.FeedbackReturner pipeline, ActorState actorState, TalkBackAnalytics analytics) {
    super(
        R.string.pref_show_context_menu_custom_action_setting_key,
        R.bool.pref_show_context_menu_custom_action_default);
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.analytics = analytics;
  }

  @Override
  public boolean accept(AccessibilityService service, AccessibilityNodeInfoCompat node) {
    return acceptCustomActionMenu(node) || acceptEditingAndSelectingMenu(node);
  }

  private static boolean acceptCustomActionMenu(AccessibilityNodeInfoCompat node) {
    List<AccessibilityActionCompat> actions = node.getActionList();
    return (actions != null && !actions.isEmpty());
  }

  private static boolean acceptEditingAndSelectingMenu(AccessibilityNodeInfoCompat node) {
    return ((node.isFocused() && Role.getRole(node) == Role.ROLE_EDIT_TEXT)
        || AccessibilityNodeInfoUtils.isNonEditableSelectableText(node));
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      AccessibilityService service, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    List<ContextMenuItem> customItems = new ArrayList<>();
    if (acceptCustomActionMenu(node)) {
      populateCustomMenuItemsForNode(service, node, customItems, includeAncestors);
    }
    List<ContextMenuItem> editingItems = new ArrayList<>();
    if (acceptEditingAndSelectingMenu(node)) {
      populateEditingAndSelectingMenuItemsForNode(service, node, editingItems, includeAncestors);
    }
    customItems.addAll(editingItems);
    return customItems;
  }

  /**
   * Populates a menu with the context menu items for a node, searching up its ancestor hierarchy if
   * the current node has no editing actions.
   *
   * @param service The parent service.
   * @param node The node to process
   * @param includeAncestors sets to {@code false} not to search its ancestor
   */
  private void populateEditingAndSelectingMenuItemsForNode(
      AccessibilityService service,
      AccessibilityNodeInfoCompat node,
      List<ContextMenuItem> items,
      boolean includeAncestors) {
    // This action has inconsistencies with EditText nodes that have
    // contentDescription attributes.
    if (TextUtils.isEmpty(node.getContentDescription())) {
      if (Role.getRole(node) == Role.ROLE_EDIT_TEXT
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node,
              AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
              AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
        ContextMenuItem moveToBeginning =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_move_to_beginning,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_move_to_beginning));
        items.add(moveToBeginning);
      }

      if (Role.getRole(node) == Role.ROLE_EDIT_TEXT
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node,
              AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
              AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)) {
        ContextMenuItem moveToEnd =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_move_to_end,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_move_to_end));
        items.add(moveToEnd);
      }
      if (Role.getRole(node) == Role.ROLE_EDIT_TEXT
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node, AccessibilityNodeInfoCompat.ACTION_CUT)) {
        ContextMenuItem cut =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_cut,
                Menu.NONE,
                service.getString(android.R.string.cut));
        items.add(cut);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          node, AccessibilityNodeInfoCompat.ACTION_COPY)) {
        ContextMenuItem copy =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_copy,
                Menu.NONE,
                service.getString(android.R.string.copy));
        items.add(copy);
      }
      if (Role.getRole(node) == Role.ROLE_EDIT_TEXT
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node, AccessibilityNodeInfoCompat.ACTION_PASTE)) {
        ContextMenuItem paste =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_paste,
                Menu.NONE,
                service.getString(android.R.string.paste));
        items.add(paste);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
              node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)
          && AccessibilityNodeInfoUtils.getText(node) != null) {
        ContextMenuItem select =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_select_all,
                Menu.NONE,
                service.getString(android.R.string.selectAll));
        items.add(select);
      }

      // TODO Use a checkable menu item once supported.
      final ContextMenuItem selectionMode;
      if (actorState.getDirectionNavigation().isSelectionModeActive()) {
        selectionMode =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_end_selection_mode,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_end_selection_mode));
      } else {
        selectionMode =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_start_selection_mode,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_start_selection_mode));
      }
      items.add(selectionMode);
    }

    EditingMenuItemClickListener listener =
        new EditingMenuItemClickListener(node, pipeline, analytics);
    for (ContextMenuItem item : items) {
      item.setOnMenuItemClickListener(listener);
      // Skip window and focued event for edit options, REFERTO.
      item.setSkipRefocusEvents(true);
      item.setSkipWindowEvents(true);
    }
  }

  /**
   * Populates a menu with the context menu items for a node, searching up its ancestor hierarchy if
   * the current node has no custom actions.
   *
   * @param service The parent service.
   * @param node The node to process
   * @param includeAncestors sets to {@code false} not to search its ancestor
   */
  private void populateCustomMenuItemsForNode(
      AccessibilityService service,
      AccessibilityNodeInfoCompat node,
      List<ContextMenuItem> menu,
      boolean includeAncestors) {
    if (node == null) {
      return;
    }

    for (AccessibilityActionCompat action : node.getActionList()) {
      CharSequence label = "";
      int id = action.getId();
      // On Android O, sometime TalkBack get fails on performing actions (mostly on notification
      // shelf). And deferring the action make the but unreproducible. REFERTO.
      boolean deferToWindowsSrable = false;

      if (AccessibilityNodeInfoUtils.isCustomAction(action)) {
        label = action.getLabel();
      } else if (id == AccessibilityNodeInfoCompat.ACTION_DISMISS) {
        label = service.getString(R.string.title_action_dismiss);
        deferToWindowsSrable = true;
      } else if (id == AccessibilityNodeInfoCompat.ACTION_EXPAND) {
        label = service.getString(R.string.title_action_expand);
        deferToWindowsSrable = true;
      } else if (id == AccessibilityNodeInfoCompat.ACTION_COLLAPSE) {
        label = service.getString(R.string.title_action_collapse);
        deferToWindowsSrable = true;
      } else if (FeatureSupport.supportDragAndDrop()) {
        if (id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START.getId()) {
          // TODO: Replace with AndroidX constants
          label =
              action.getLabel() == null
                  ? service.getString(R.string.title_action_drag_start)
                  : action.getLabel();
        } else if (id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_DROP.getId()) {
          label =
              action.getLabel() == null
                  ? service.getString(R.string.title_action_drag_drop)
                  : action.getLabel();
        } else if (id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_CANCEL.getId()) {
          label =
              action.getLabel() == null
                  ? service.getString(R.string.title_action_drag_cancel)
                  : action.getLabel();
        }
      }

      if (TextUtils.isEmpty(label)) {
        continue;
      }

      ContextMenuItem item = ContextMenu.createMenuItem(service, Menu.NONE, id, Menu.NONE, label);
      item.setOnMenuItemClickListener(
          new CustomMenuItemClickListener(id, node, pipeline, analytics));
      if ((Build.VERSION.SDK_INT == VERSION_CODES.O || Build.VERSION.SDK_INT == VERSION_CODES.O_MR1)
          && deferToWindowsSrable) {
        item.setDeferredType(DeferredType.WINDOWS_STABLE);
      }
      item.setCheckable(false);
      menu.add(item);
    }

    if (!includeAncestors) {
      return;
    }

    if (menu.isEmpty()) {
      AccessibilityNodeInfoCompat parentNode = node.getParent();
      populateCustomMenuItemsForNode(service, parentNode, menu, /* includeAncestors= */ true);
    }
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_custom_action);
  }

  /** Listener may be shared by multi-contextItems. */
  private static class CustomMenuItemClickListener extends AbstractOnContextMenuItemClickListener {
    private final int id;

    CustomMenuItemClickListener(
        int id,
        AccessibilityNodeInfoCompat node,
        Pipeline.FeedbackReturner pipeline,
        TalkBackAnalytics analytics) {
      super(node, pipeline, analytics);
      this.id = id;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      try {
        EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
        boolean ret = pipeline.returnFeedback(eventId, Feedback.nodeAction(node, id));
        switch (id) {
          case AccessibilityNodeInfoCompat.ACTION_DISMISS:
          case AccessibilityNodeInfoCompat.ACTION_EXPAND:
          case AccessibilityNodeInfoCompat.ACTION_COLLAPSE:
            analytics.onLocalContextMenuAction(MENU_TYPE_CUSTOM_ACTION, id);
            break;
          default:
            analytics.onLocalContextMenuAction(MENU_TYPE_CUSTOM_ACTION, MENU_ITEM_UNKNOWN);
            break;
        }

        return ret;
      } finally {
        clear();
      }
    }
  }

  /** Listener may be shared by multi-contextItems. */
  private static class EditingMenuItemClickListener extends AbstractOnContextMenuItemClickListener {

    public EditingMenuItemClickListener(
        AccessibilityNodeInfoCompat node,
        Pipeline.FeedbackReturner pipeline,
        TalkBackAnalytics analytics) {
      super(node, pipeline, analytics);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      try {
        if (item == null) {
          return true;
        }

        final int itemId = item.getItemId();
        analytics.onLocalContextMenuAction(MENU_TYPE_EDIT_OPTIONS, itemId);
        final boolean result;
        EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for menu events.
        if (itemId == R.id.edittext_breakout_move_to_beginning) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, CURSOR_TO_BEGINNING));
        } else if (itemId == R.id.edittext_breakout_move_to_end) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, CURSOR_TO_END));
        } else if (itemId == R.id.edittext_breakout_cut) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, CUT));
        } else if (itemId == R.id.edittext_breakout_copy) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, COPY));
        } else if (itemId == R.id.edittext_breakout_paste) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, PASTE));
        } else if (itemId == R.id.edittext_breakout_select_all
            && !TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node))) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, SELECT_ALL));
        } else if (itemId == R.id.edittext_breakout_start_selection_mode) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, START_SELECT));
        } else if (itemId == R.id.edittext_breakout_end_selection_mode) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, END_SELECT));
        } else {
          result = false;
        }

        if (result) {
          TalkBackService service = TalkBackService.getInstance();
          if (service != null) {
            service.getAnalytics().onTextEdited();
          }
        } else {
          pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
        }
        return true;
      } finally {
        clear();
      }
    }
  }
}
