Calc+

Calculator thats actually a hidden file / folder / document storage encrypted and hiiden store locked with a passphrase / pin code key aswell as a normal looking calculatror that fully works normally (when not logged into hidden storage part of the app)

called Calc-Plus.apk

Comes with a default pin and once used (Default Pin 12345678) then when entered, it opens up settings and allows user to set a new pin (at least 4 digits) and once a new pin is entered and confirmed and saved / updated from the default or old previous pin then it logs out and when entered again Settings screen for the App again shows so they can Set a new pin reset Calc+ Storage (removes all stored encypted docs / files / folders) allows transfere from os default storage to and from the Calc+ encrypted Storage Hidden Vault) allow settings that self destructs the encrpyted data if pin entered incorrectly 10 times (allow changing how many failed attemps before self destruct).
allow encrypted (using current pin code used at the time) backup and restore to file in os storage location and allow save to google drive (anyone can link there account and transfere, same for one drive, allow sending backup using default share for example telegram

allow storing of secure notes aswell as files docs / folders etc all encrypted using the current in use pin code

allow search box to find for string / filename folder name
allow selection of file / folder / document and show options like move to, copy to, delete
allow restore of encrypted backup file (using pin so if you want it on another device it will work as long as pin is correct!

project location /home/jay/Documents/Scripts/AI/OpenCode/Calc/

use github workflows to build the app and put finally release in apk folder in the project location

Dont edit this file

Never change anything in Backup folders (if it exists) but you can use them as a read-only reference if a mistake is made and you need to fix something

save changes to file(s) in question

then after files are added / edited then save any changes made to changes.txt

Implement persistent error handling and debugging throughout the project. Every failure, exception, or unexpected state should generate a clear error code, detailed debug output, and useful diagnostic information to help identify the exact cause quickly.
Do not remove debugging systems after issues are fixed — keep all error codes, logging, stack traces, validation checks, and diagnostic tools permanently integrated so that any future bugs, crashes, or unexpected behaviour can be traced and resolved efficiently.

always use same key-store for each app made via github workflows so it can update correctly without requiring uninstallation

Save changes to changes.txt (create if not exists)

tell me when ready to test (stay quiet after acknowledging you got the message / request / mission every time and stay quiet till its ready to test and respond only if fully complete  or if you need input from me or if I ask for an update)!

when giving final github release link (where applicable), make sure it points to the newest release but without the tag or filename so I can see the correct location without direct downloading the file as thats best practice!

each app needs an About section showing (About after Settings tab / screen)
in about section it should say Made by jnetai.com 
The full version number (same as github release version tag) 
add a Check for update button (so internet permissions required) to check latest release version (tag in full) if update available show open button that opens in browser latest releases
add a Share App button so users can share the app. (that shares latest vewrsion)
 
each update should use same key store unique to this app but never changes so the app can update and not require uninstall of the app to update it.

each app should be dark centered themed and allow space at bottom so buttons or elements at the bottom of the app should not be cut off, it should look professional.

app compatibility: apps needs to work on samsung s8 and onwards and google pixel 6 and onwards
full path to Downloads is /storage/emulated/0/Download/ (called Downloads as an alias in android)

in releases on github a meaningful name should be used for example Tetris.apk (no need for a debug version of any app or game for android just put the debug version as the main version!

github api tokens / passwords etc can be found in /home/jay/Documents/Scripts/AI/openclaw/password-vault/

build the releases via github actions / workflows (not locally)
create usage instructions.txt showing all features and how to use / login  / set new pin etc


