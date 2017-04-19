package maulik.sendanything1;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Iterator;
import java.util.List;

public class SendAnythingService extends AccessibilityService {

    String textWritten = "";
    static AccessibilityNodeInfo messageBoxNode;
    static Context c;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        c = getApplicationContext();
        Log.i("ServiceTriggered", Integer.toString(event.getEventType()));

        if (AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED == event.getEventType()) {
            Log.i("TextChanged", "yes");
            messageBoxNode = getNodeWithId(event, "com.whatsapp:id/entry");

            if (messageBoxNode != null) {
                textWritten = messageBoxNode.getText().toString();
                Log.i("TextWritten", textWritten);

                if (textWritten.equalsIgnoreCase("s.a")) {

                    Bundle arguments = new Bundle();
                    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD);
                    arguments.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                            true);
                    messageBoxNode.performAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                            arguments);

                    ClipData clip = ClipData.newPlainText("label", "");
                    ClipboardManager manager = (ClipboardManager) c.getSystemService(CLIPBOARD_SERVICE);
                    ClipData oldClip = manager.getPrimaryClip();
                    manager.setPrimaryClip(clip);
                    messageBoxNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                    if(oldClip!=null)
                        manager.setPrimaryClip(oldClip);

                    Toast.makeText(getApplicationContext(), "Send", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(getApplicationContext(), DriveUploadActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }

                if(textWritten.equals(DriveUploadActivity.fileDownloadLink)) {
                    DriveUploadActivity.fileDownloadLink = "";
                }
            }
        }

        if (AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED == event.getEventType()) {
            messageBoxNode = getNodeWithId(event, "com.whatsapp:id/entry");
            if (messageBoxNode != null) {
                if (!DriveUploadActivity.fileDownloadLink.equals("")) {
//                    Bundle bundle = new Bundle();
//                    String fileLink = DriveUploadActivity.fileDownloadLink;
//                    bundle.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", fileLink);
//                    messageBoxNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);
                    Bundle arguments = new Bundle();
                    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD);
                    arguments.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                            true);
                    messageBoxNode.performAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                            arguments);

                    ClipData clip = ClipData.newPlainText("label", DriveUploadActivity.fileDownloadLink);
                    ClipboardManager manager = (ClipboardManager) getApplicationContext().getSystemService(CLIPBOARD_SERVICE);
                    manager.setPrimaryClip(clip);
                    messageBoxNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                    if(DriveUploadActivity.oldClip!=null)
                        manager.setPrimaryClip(DriveUploadActivity.oldClip);

                }
            }
        }

    }

    public static void setLinkText() {
        if (messageBoxNode != null) {
            if (DriveUploadActivity.fileDownloadLink != null) {
//                Bundle bundle = new Bundle();
//                String fileLink = DriveUploadActivity.fileDownloadLink;
//                bundle.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", fileLink);
//                messageBoxNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);
                Bundle arguments = new Bundle();
                arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD);
                arguments.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                        true);
                messageBoxNode.performAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                        arguments);

                ClipData clip = ClipData.newPlainText("label", DriveUploadActivity.fileDownloadLink);
                ClipboardManager manager = (ClipboardManager) c.getSystemService(CLIPBOARD_SERVICE);
                manager.setPrimaryClip(clip);
                messageBoxNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                if(DriveUploadActivity.oldClip!=null)
                    manager.setPrimaryClip(DriveUploadActivity.oldClip);

            }
        }
    }

    private AccessibilityNodeInfo getNodeWithId(AccessibilityEvent event, String id) {
        AccessibilityNodeInfo rootNode = event.getSource();
        AccessibilityNodeInfo childNode = null;
        List l = rootNode.findAccessibilityNodeInfosByViewId(id);
        for (Object aL : l) {
            Log.i("EntryNode", "Available");
            childNode = (AccessibilityNodeInfo) aL;
        }
        return childNode;
    }

    @Override
    public void onInterrupt() {

    }
}
