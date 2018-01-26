/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /home/ezio/Android/abc_packages_apps_Launcher3/src/main/aidl/com/google/android/libraries/launcherclient/ILauncherOverlayCallback.aidl
 */
package com.google.android.libraries.launcherclient;
public interface ILauncherOverlayCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.google.android.libraries.launcherclient.ILauncherOverlayCallback
{
private static final java.lang.String DESCRIPTOR = "com.google.android.libraries.launcherclient.ILauncherOverlayCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.google.android.libraries.launcherclient.ILauncherOverlayCallback interface,
 * generating a proxy if needed.
 */
public static com.google.android.libraries.launcherclient.ILauncherOverlayCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.google.android.libraries.launcherclient.ILauncherOverlayCallback))) {
return ((com.google.android.libraries.launcherclient.ILauncherOverlayCallback)iin);
}
return new com.google.android.libraries.launcherclient.ILauncherOverlayCallback.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_overlayScrollChanged:
{
data.enforceInterface(DESCRIPTOR);
float _arg0;
_arg0 = data.readFloat();
this.overlayScrollChanged(_arg0);
return true;
}
case TRANSACTION_overlayStatusChanged:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.overlayStatusChanged(_arg0);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.google.android.libraries.launcherclient.ILauncherOverlayCallback
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void overlayScrollChanged(float progress) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeFloat(progress);
mRemote.transact(Stub.TRANSACTION_overlayScrollChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
@Override public void overlayStatusChanged(int status) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(status);
mRemote.transact(Stub.TRANSACTION_overlayStatusChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_overlayScrollChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_overlayStatusChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void overlayScrollChanged(float progress) throws android.os.RemoteException;
public void overlayStatusChanged(int status) throws android.os.RemoteException;
}
