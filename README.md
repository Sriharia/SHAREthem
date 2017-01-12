# SHAREthem
![App_Icon](screens/ic_launcher.png?raw=true "app-icon")

SHAREthem library facilitates P2P file sharing and transfers between devices using WiFi Hotspot.  
Also an attempt to simulate popular [SHAREit](https://play.google.com/store/apps/details?id=com.lenovo.anyshare.gps&hl=en) App functionality and improvise it by supporting multiple receivers at one go
 
Library also supports App (Android) to Web/Mobile Browser transfers if Receiver has no App installed.

 
 Download the Working version from Play Store

[![PlayStore][playstore-image]][playstore-url]
[playstore-image]: screens/google-play-badge.png
[playstore-url]: https://play.google.com/store/apps/details?id=com.tml.sharethem.demo


### Developed by
[Srihari Yachamaneni](https://github.com/Sriharia) ([@srihari_y](https://twitter.com/srihari_y))

### Features

* File transfers and sharing.
* Supports downloads on Browser if Receiver has no App installed.
* Easy to Implement.
* No permissions required. (library handles them based on targetSdkVersion)

### Usage

## SHARE mode
To start SHARE mode, you need to pass an array of strings holding references of files you want to share along with port(optional) and sender name using an Intent to start SHAREthemActivity

```
Intent intent = new Intent(getApplicationContext(), SHAREthemActivity.class);
intent.putExtra(ShareService.EXTRA_FILE_PATHS, new String[]{[path to file1], [path to file2]}); // mandatory
intent.putExtra(ShareService.EXTRA_PORT, 52287); //optional but preferred
intent.putExtra(ShareService.EXTRA_SENDER_NAME, "Sri"); //optional
startActivity(intent);
```

`ShareService.EXTRA_FILE_PATHS`: holds location references to local files on device.

`ShareService.EXTRA_PORT` (optional): in case you want to start SHAREserver on a specific port. Passing 0 or skipping this lets system to assign its own. One of the downside of letting system to do this is SSID may be not the same for a subsequent Sharing session. (PORT number is used in algorithm to generate SSID)

`ShareService.EXTRA_SENDER_NAME` (optional): used by Receiver to display connection information.

## Receiver mode
Starting receiver mode is pretty simple as no intent extras needed. Receiver Activity starts scanning for senders automatically, you can turn off Receiver mode anytime though.

```
startActivity(new Intent(getApplicationContext(), ReceiverActivity.class));
```

End with an example of getting some data out of the system or using it for a little demo

### Read more about implementation on my blog [here](https://srihary.com/) 

## DEMO Built With

* [android-filepicker](https://github.com/Angads25/android-filepicker) - Android Library to select files/directories from Device Storage.

## IMPORTANT NOTE
* increasing targetSdkVersion version might impact behaviour of this library
    1. if targetSdkVersion >= 23
        * ShareActivity has to check for System Write permissions to proceed
        * Get Wifi Scan results method needs GPS to be ON and COARSE location permission.
    2. library checks the targetSdkVersion to take care of above scenarios if targetSdkVersion > 20
    3. If an application's targetSdkVersion is LOLLIPOP or newer, network communication may not use Wi-Fi even if Wi-Fi is connected with no interner.
this might impact when Receiver connectivity to SHAREthem hotspot, library checks for this scenario and prompts user to disable data
For more info: https://developer.android.com/reference/android/net/wifi/WifiManager.html#enableNetwork

## Troubleshooting
 
#### Receiver Connected but not able to display Sender info:
  As mentioned above, on API level 21 & above, n/w communication may not use Wifi with no internet access if MOBILE DATA has internet access. Library can prompt to disable MOBILE DATA if this scenario is met, but not yet implemented.
  So turn-off mobile to make Receiver work.
#### ReceiverActivity cannot connect to SHAREthem Wifi:
Receiver tries to connect to SHAREthem Wifi in every way possible, but throws an error dialog if it fails. Dialog says to manually select SHAREthem Wifi on WIfi Settings.
As a last resort, you can always manually connect to Sender Wifi.
    
## Known Issues
* On some devices with API level 21 and above, ```connectToOpenHotspot()``` method on WifiUtils disables other Wifi networks in-order to connect to SHAREthem WIfi Hotspot. Ideally increasing priority should be enough for making WifiConfiguration but recent Android version are making it hard for Wifi n.ws with no internet connectivity.
* Enabling SHARE mode when System Wifi Hotspot is already active (enabled by user via Settings) might not work.
 
## Screenshots
![Demo Activity](screens/screenshot-3.jpg?raw=true "Demo App using SHAREthem Library")
![File explorer](screens/screenshot-1.jpg?raw=true "android File-Explorer")
![SHAREthem Activity](screens/device-2017-01-09-235222.png?raw=true "Share Activity displaying conn info and client connected")
![SHAREthem Service Notification](screens/screenshot-2.jpg?raw=true "Share service with foreground notification and stop action")
![Receiver Activity](screens/device-2017-01-09-233902.png?raw=true "Receiver Listing fragment displaying all downloads from Sender")
![Android Download Manager Notification](screens/device-2017-01-09-235236.png?raw=true "Download Status Notifications from ADM")
![Web Receiver](screens/screenshot-web.png?raw=true "Web Receiver")
## License

Copyright 2017 Srihari Yachamaneni

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
 
  http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
  
 see the [LICENSE.md](LICENSE.md) file for details

