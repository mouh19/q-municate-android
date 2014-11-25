package com.quickblox.q_municate.ui.chats;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.quickblox.chat.model.QBDialog;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate.ui.base.BaseLogeableActivity;
import com.quickblox.q_municate.ui.dialogs.ConfirmDialog;
import com.quickblox.q_municate.ui.friends.FriendDetailsActivity;
import com.quickblox.q_municate.ui.profile.ProfileActivity;
import com.quickblox.q_municate.ui.uihelper.SimpleActionModeCallback;
import com.quickblox.q_municate.ui.uihelper.SimpleTextWatcher;
import com.quickblox.q_municate.ui.views.RoundedImageView;
import com.quickblox.q_municate.utils.Consts;
import com.quickblox.q_municate.utils.ImageUtils;
import com.quickblox.q_municate.utils.ReceiveFileFromBitmapTask;
import com.quickblox.q_municate.utils.ReceiveUriScaledBitmapTask;
import com.quickblox.q_municate_core.core.command.Command;
import com.quickblox.q_municate_core.db.managers.ChatDatabaseManager;
import com.quickblox.q_municate_core.db.managers.UsersDatabaseManager;
import com.quickblox.q_municate_core.models.AppSession;
import com.quickblox.q_municate_core.models.GroupDialog;
import com.quickblox.q_municate_core.models.MessagesNotificationType;
import com.quickblox.q_municate_core.models.User;
import com.quickblox.q_municate_core.qb.commands.QBLeaveGroupDialogCommand;
import com.quickblox.q_municate_core.qb.commands.QBLoadGroupDialogCommand;
import com.quickblox.q_municate_core.qb.commands.QBUpdateGroupDialogCommand;
import com.quickblox.q_municate_core.qb.helpers.QBMultiChatHelper;
import com.quickblox.q_municate_core.service.QBService;
import com.quickblox.q_municate_core.service.QBServiceConsts;
import com.quickblox.q_municate_core.utils.ConstsCore;
import com.quickblox.q_municate_core.utils.DialogUtils;
import com.quickblox.q_municate_core.utils.ErrorUtils;
import com.quickblox.q_municate_core.utils.FriendUtils;
import com.quickblox.users.model.QBUser;
import com.soundcloud.android.crop.Crop;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GroupDialogDetailsActivity extends BaseLogeableActivity implements ReceiveFileFromBitmapTask.ReceiveFileListener, AdapterView.OnItemClickListener, ReceiveUriScaledBitmapTask.ReceiveUriScaledBitmapListener {

    public static final int UPDATE_DIALOG_REQUEST_CODE = 100;
    public static final int RESULT_LEAVE_GROUP = 2;

    private EditText groupNameEditText;
    private TextView participantsTextView;
    private ListView friendsListView;
    private TextView onlineParticipantsTextView;
    private RoundedImageView avatarImageView;

    private String dialogId;
    private GroupDialog groupDialog;

    private Object actionMode;
    private boolean closeActionMode;
    private boolean isNeedUpdateAvatar;
    private Uri outputUri;

    private Bitmap avatarBitmapCurrent;
    private QBDialog currentDialog;
    private String groupNameCurrent;

    private String photoUrlOld;
    private String groupNameOld;

    private ImageUtils imageUtils;
    private GroupDialogOccupantsAdapter groupDialogOccupantsAdapter;
    private QBMultiChatHelper multiChatHelper;

    private List<MessagesNotificationType> currentNotificationTypeList;
    private ArrayList<Integer> addedFriendIdsList;

    public static void start(Activity context, String dialogId) {
        Intent intent = new Intent(context, GroupDialogDetailsActivity.class);
        intent.putExtra(QBServiceConsts.EXTRA_DIALOG_ID, dialogId);
        context.startActivityForResult(intent, UPDATE_DIALOG_REQUEST_CODE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_dialog_details);
        dialogId = (String) getIntent().getExtras().getSerializable(QBServiceConsts.EXTRA_DIALOG_ID);
        currentDialog = ChatDatabaseManager.getDialogByDialogId(this, dialogId);
        groupDialog = new GroupDialog(currentDialog);
        imageUtils = new ImageUtils(this);

        initUI();
        initUIWithData();
        addActions();
        startLoadGroupDialog();

        currentNotificationTypeList = new ArrayList<MessagesNotificationType>();
    }

    @Override
    public void onConnectedToService(QBService service) {
        if (multiChatHelper == null) {
            multiChatHelper = (QBMultiChatHelper) service.getHelper(QBService.MULTI_CHAT_HELPER);
        }
    }

    @Override
    public void onUriScaledBitmapReceived(Uri originalUri) {
        hideProgress();
        startCropActivity(originalUri);
    }

    private void startLoadGroupDialog() {
        QBLoadGroupDialogCommand.start(this, currentDialog, groupDialog.getRoomJid());
    }

    public void changeAvatarOnClick(View view) {
        canPerformLogout.set(false);
        imageUtils.getImage();
    }

    private void initUI() {
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        avatarImageView = _findViewById(R.id.avatar_imageview);
        groupNameEditText = _findViewById(R.id.name_textview);
        participantsTextView = _findViewById(R.id.participants_textview);
        friendsListView = _findViewById(R.id.chat_friends_listview);
        onlineParticipantsTextView = _findViewById(R.id.online_participants_textview);
    }

    private void initUIWithData() {
        groupNameEditText.setText(groupDialog.getName());
        participantsTextView.setText(getString(R.string.gdd_participants, groupDialog.getOccupantsCount()));
        onlineParticipantsTextView.setText(getString(R.string.gdd_online_participants,
                groupDialog.getOnlineOccupantsCount(), groupDialog.getOccupantsCount()));
        if (!isNeedUpdateAvatar) {
            loadAvatar(groupDialog.getPhotoUrl());
        }
        updateOldGroupData();
    }

    private void loadAvatar(String photoUrl) {
        ImageLoader.getInstance().displayImage(photoUrl, avatarImageView,
                Consts.UIL_GROUP_AVATAR_DISPLAY_OPTIONS);
    }

    private void initListView() {
        groupDialogOccupantsAdapter = getFriendsAdapter();
        friendsListView.setAdapter(groupDialogOccupantsAdapter);
        friendsListView.setOnItemClickListener(this);
    }

    private void addActions() {
        UpdateGroupFailAction updateGroupFailAction = new UpdateGroupFailAction();

        addAction(QBServiceConsts.LOAD_GROUP_DIALOG_SUCCESS_ACTION, new LoadGroupDialogSuccessAction());
        addAction(QBServiceConsts.LOAD_GROUP_DIALOG_FAIL_ACTION, failAction);

        addAction(QBServiceConsts.LEAVE_GROUP_DIALOG_SUCCESS_ACTION, new LeaveGroupDialogSuccessAction());
        addAction(QBServiceConsts.LEAVE_GROUP_DIALOG_FAIL_ACTION, failAction);

        addAction(QBServiceConsts.UPDATE_GROUP_DIALOG_SUCCESS_ACTION, new UpdateGroupDialogSuccessAction());
        addAction(QBServiceConsts.UPDATE_GROUP_DIALOG_FAIL_ACTION, updateGroupFailAction);

        updateBroadcastActionList();
    }

    protected GroupDialogOccupantsAdapter getFriendsAdapter() {
        return new GroupDialogOccupantsAdapter(this, groupDialog.getOccupantList());
    }

    private void showLeaveGroupDialog() {
        ConfirmDialog dialog = ConfirmDialog.newInstance(R.string.dlg_leave_group, R.string.dlg_confirm);
        dialog.setPositiveButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                leaveGroup();
            }
        });
        dialog.show(getFragmentManager(), null);
    }

    private void leaveGroup() {
        showProgress();
        currentNotificationTypeList.add(MessagesNotificationType.LEAVE_DIALOG);
        sendNotificationToGroup();
        QBLeaveGroupDialogCommand.start(GroupDialogDetailsActivity.this, groupDialog.getRoomJid());
    }

    private void initTextChangedListeners() {
        groupNameEditText.addTextChangedListener(new GroupNameTextWatcherListener());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (actionMode != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            groupNameEditText.setText(groupDialog.getName());
            closeActionMode = true;
            ((ActionMode) actionMode).finish();
            return true;
        } else {
            closeActionMode = false;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.group_dialog_details_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                navigateToParent();
                return true;
            case R.id.action_add:
                startAddFriendsActivity();
                return true;
            case R.id.action_leave:
                showLeaveGroupDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Crop.REQUEST_CROP) {
            handleCrop(resultCode, data);
        } else if (requestCode == AddFriendsToGroupActivity.RESULT_ADDED_FRIENDS) {
            if (data != null) {
                handleAddedFriends(data);
            }
        } else if (requestCode == ImageUtils.GALLERY_INTENT_CALLED && resultCode == RESULT_OK) {
            Uri originalUri = data.getData();
            if (originalUri != null) {
                showProgress();
                new ReceiveUriScaledBitmapTask(this).execute(imageUtils, originalUri);
            }
        }
        canPerformLogout.set(true);
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void handleAddedFriends(Intent data) {
        addedFriendIdsList = (ArrayList<Integer>) data.getSerializableExtra(QBServiceConsts.EXTRA_FRIENDS);
        if (addedFriendIdsList != null) {
            currentNotificationTypeList.add(MessagesNotificationType.ADDED_DIALOG);
            sendNotificationToGroup();
        }
    }

    private void startAddFriendsActivity() {
        int countUnselectedFriendsInChat = UsersDatabaseManager.getFriendsFilteredByIds(this,
                FriendUtils.getFriendIds(groupDialog.getOccupantList())).getCount();
        if (countUnselectedFriendsInChat != ConstsCore.ZERO_INT_VALUE) {
            AddFriendsToGroupActivity.start(this, groupDialog);
        } else {
            DialogUtils.showLong(this, getResources().getString(R.string.gdd_all_friends_is_in_group));
        }
    }

    private void handleCrop(int resultCode, Intent result) {
        if (resultCode == RESULT_OK) {
            isNeedUpdateAvatar = true;
            avatarBitmapCurrent = imageUtils.getBitmap(outputUri);
            avatarImageView.setImageBitmap(avatarBitmapCurrent);
            startAction();
        } else if (resultCode == Crop.RESULT_ERROR) {
            DialogUtils.showLong(this, Crop.getError(result).getMessage());
        }
    }

    private void startCropActivity(Uri originalUri) {
        outputUri = Uri.fromFile(new File(getCacheDir(), Crop.class.getName()));
        new Crop(originalUri).output(outputUri).asSquare().start(this);
    }

    private void startAction() {
        if (actionMode != null) {
            return;
        }
        actionMode = startActionMode(new ActionModeCallback());
    }

    private void updateCurrentUserData() {
        groupNameCurrent = groupNameEditText.getText().toString();
    }

    private void updateUserData() {
        updateCurrentUserData();
        if (isGroupDataChanged()) {
            saveChanges();
        }
    }

    private boolean isGroupDataChanged() {
        return !groupNameCurrent.equals(groupNameOld) || isNeedUpdateAvatar;
    }

    private void saveChanges() {
        if (!isUserDataCorrect()) {
            DialogUtils.showLong(this, getString(R.string.gdd_name_not_entered));
            return;
        }

        if (!currentDialog.getName().equals(groupNameCurrent)) {
            currentDialog.setName(groupNameCurrent);

            currentNotificationTypeList.add(MessagesNotificationType.NAME_DIALOG);
        }

        if (isNeedUpdateAvatar) {
            new ReceiveFileFromBitmapTask(this).execute(imageUtils, avatarBitmapCurrent, true);

            currentNotificationTypeList.add(MessagesNotificationType.PHOTO_DIALOG);
        } else {
            updateGroupDialog(null);
        }

        showProgress();
    }

    private void sendNotificationToGroup() {
        for (MessagesNotificationType messagesNotificationType : currentNotificationTypeList) {
            try {
                multiChatHelper.sendNotificationToFriends(currentDialog, messagesNotificationType, addedFriendIdsList);
            } catch (QBResponseException e) {
                ErrorUtils.logError(e);
                hideProgress();
            }
        }
        currentNotificationTypeList.clear();
    }

    private boolean isUserDataCorrect() {
        return !TextUtils.isEmpty(groupNameCurrent);
    }

    private void updateOldGroupData() {
        groupNameOld = groupDialog.getName();
        photoUrlOld = groupDialog.getPhotoUrl();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
        User selectedFriend = groupDialogOccupantsAdapter.getItem(position);
        if (selectedFriend != null) {
            startFriendProfile(selectedFriend);
        }
    }

    private void startFriendProfile(User selectedFriend) {
        QBUser currentUser = AppSession.getSession().getUser();
        if (currentUser.getId() == selectedFriend.getUserId()) {
            ProfileActivity.start(GroupDialogDetailsActivity.this);
        } else {
            FriendDetailsActivity.start(GroupDialogDetailsActivity.this, selectedFriend.getUserId());
        }
    }

    private void resetGroupData() {
        groupNameEditText.setText(groupNameOld);
        isNeedUpdateAvatar = false;
        loadAvatar(photoUrlOld);
    }

    private void updateGroupDialog(File imageFile) {
        QBUpdateGroupDialogCommand.start(this, currentDialog, imageFile);
    }

    @Override
    public void onCachedImageFileReceived(File imageFile) {
        updateGroupDialog(imageFile);
    }

    @Override
    public void onAbsolutePathExtFileReceived(String absolutePath) {

    }

    private class GroupNameTextWatcherListener extends SimpleTextWatcher {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!groupNameOld.equals(s.toString())) {
                startAction();
            }
        }
    }

    private class ActionModeCallback extends SimpleActionModeCallback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (!closeActionMode) {
                updateUserData();
            }
            actionMode = null;
        }
    }

    private class LoadGroupDialogSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            groupDialog = (GroupDialog) bundle.getSerializable(QBServiceConsts.EXTRA_GROUP_DIALOG);
            updateOldGroupData();
            initUIWithData();
            initTextChangedListeners();
            initListView();
            hideProgress();
        }
    }

    private class LeaveGroupDialogSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            hideProgress();
            setResult(RESULT_LEAVE_GROUP, getIntent());
            finish();
        }
    }

    private class UpdateGroupDialogSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            currentDialog = (QBDialog) bundle.getSerializable(QBServiceConsts.EXTRA_DIALOG);
            groupDialog = new GroupDialog(ChatDatabaseManager.getDialogByDialogId(
                    GroupDialogDetailsActivity.this, currentDialog.getDialogId()));
            updateOldGroupData();

            sendNotificationToGroup();
            hideProgress();
        }
    }

    private class UpdateGroupFailAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            Exception exception = (Exception) bundle.getSerializable(QBServiceConsts.EXTRA_ERROR);
            DialogUtils.showLong(GroupDialogDetailsActivity.this, exception.getMessage());
            resetGroupData();
            hideProgress();
        }
    }
}