package com.tml.sharethem.sender;

/**
 * Created by Sri on 18/12/16.
 */
interface FileTransferStatusListener {
    void onBytesTransferProgress(String ip, String fileName, long totalSize, String spped, long currentSize, int percentageUploaded);
    void onBytesTransferCompleted(String ip, String fileName);
    void onBytesTransferStarted(String ip, String fileName);
    void onBytesTransferCancelled(String ip, String error, String fileName);
}
