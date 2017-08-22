# CSMA/CN: Carrier Sense Multiple Access / Collision Notification
## CS546 Individual Class Term Project (Spring 2016, KAIST)

### 1. Requirements


| Device name| Requirements|
|-----------------|----------------------------------------------------------------------------------------------------------------------------|
| Android Device  | Android version 5.0 or 5.1 (Lollipop) (Android 6.0 or higher would not work properly due to its changed permission system) |
|                 | Bluetooth Low Energy (4.0) Capability                                                                                      |
|                 | 802.11n Wi-Fi Capability                                                                                                   |
| Custom Wi-Fi AP | Raspberry Pi + Ubuntu for Raspberry Pi                                                                                     |
|                 | Bluetooth Low Energy (4.0) Capability                                                                                      |
|                 | 802.11n Wi-Fi Capability                                                                                                   |

### 2. Sample Testing Environment

I tested CSMA/CN in the following environments to implement the project and measure the performance.

- Android device: Motorola Nexus 6 with Android 5.1, Samsung Galaxy A7 2016 with Android 5.1.1
- Customized Wi-Fi AP: Raspberry Pi model B with Wi-Fi USB adapter and BLE USB adapter
- Wi-Fi USB adapter: NEXT-202N mini (RTL8188CUS 802.11n WLAN adapter)
- BLE USB adapter: NEXT-204BT (Cambridge Silicon Radio Ltd Bluetooth Dongle)

![setup](/images/setup.png)

I strongly recommend you to use the same setup to test it.


### 3. Building an APK and installing it in Android

- Run Android Studio and import the Android Studio project source code.
- After finishing automatic build process, run the app at the connected device.

### 4. Installing Ubuntu packages in Raspberry Pi

- You need to install several packages in your Raspberry Pi Ubuntu.
    ```
    sudo apt-get update
    sudo apt-get install build-essential
    sudo apt-get install hostapd isc-dhcp-server
    sudo apt-get install Bluetooth bluez libbluetooth-dev libudev-dev curl -sLS https://apt.adafruit.com/add | sudo bash
    sudo apt-get install node
    ```
- Then, Edit `/etc/dhcp/dhcpd.conf` as follows.
    - Modify option domain name "example.org" to "cs546.kaist.ac.kr"
    - Modify option domain name servers ns1.example.org, ns2.example.org to 8.8.8.8, 8.8.8.4.
    - Uncomment authoritative.
    - Add the following lines to the bottom:
        ```
        subnet 192.168.43.0 netmask 255.255.255.0 {
            range 192.168.43.10 192.168.43.128;
            option broadcast-address 192.168.43.255;
            option routers 192.168.43.1;
        }
        ```

- Edit `/etc/default/isc-dhcp-server` as follows.
    - Modify `INTERFACES=""` to `INTERFACE="wlan0"`.

- Edit `/etc/network/interfaces` as follows.
    - Add the following lines to the bottom:
        ```
        iface wlan0 inet static
        address 192.168.43.1
        netmask 255.255.255.0
        ```

- Assign the static IP address to wlan0 with `sudo ifconfig wlan0 192.168.43.1`.  
It runs as a Wi-Fi AP with a router IP address 192.168.43.1 that assigns IP addresses with the range 192.168.43.10 ~ 192.168.43.128 to clients.

- Create `/etc/hostapd/hostapd.conf` file with the following contents:
    ```
    interface=wlan0
    driver=rtl871xdrv
    ssid=RPiAP <the SSID that you want>
    hw_mode=g
    ieee80211n=1
    channel=11 <the Wi-Fi channel that you want. 1~11> wpa=3
    ignore_broadcast_ssid=0
    country_code=KR
    wpa_passpharse=password <the password that you want> wpa_key_mgmt=WPA-PSK
    wpa_pairwise=TKIP
    rsn_pairwise=CCMP
    auth_algs=1
    wmm_enabled=1
    wme_enabled=1
    ```

- Modify `/etc/default/hostapd` as follows:
    - Find the line `#DAEMON_CONFIG=""` and edit it to `DAEMON_CONFIG="/etc/hostapd/hostapd.conf"`.

- Run the following command to make a tunnel between Wi-Fi and ethernet.
    ```
    sudo sh –c “echo 1 > /proc/sys/net/ipv4/ip_forward”
    ```

    **This operation will be lost when you reboot the machine.**

- Run the following commands to create network translation between eth0 and wlan0.
    ```
    sudo iptables –t nat –A POSTROUTING –o eth0 –j MASQUERADE
    sudo iptables –A FORWARD –i eth0 –o wlan0 –m state --state RELATED,ESTABLISHED –j ACCEPT
    sudo iptables –A FORWARD –i wlan0 –o eth0 –j ACCEPT
    ```

- Download the following file to update hostapd. This statement is necessary due to a bug of hostapd driver.
    ```
    wget http://adafruit-download.s3.amazonaws.com/adafruit_hostapd_14128.zip
    unzip adafruit_hostapd_14128.zip
    sudo mv /usr/sbin/hostapd /usr/sbin/hostapd.ORIG
    sudo mv hostapd /usr/sbin
    sudo chmod 755 /usr/sbin/hostapd
    ```

- Run the services as follows.
    ```
    sudo service hostapd restart
    sudo service isc-dhcp-server restart
    ```

    You should see RPiAP SSID in your smartphone after this moment.

- Run the node.js server as follows.
    ```
    sudo node <server_files_location>/udp.js false false
    ```

    ![server_run](/images/server_run.png)

    **Parameter explanation:**  
        - the first one represents the existence of hidden terminal problem.  
        - the second one represents the use of CSMA/CN.
        - false false or false true: The transmission without hidden terminal problem. Using CSMA/CN will have no meaningful effect.
        - true false: The transmission with hidden terminal problem and without CSMA/CN. The performance will drastically reduced.
        - true true: The tramission with hidden terminal problem and also with CSMA/CN.

    Root permission is required for node.js to use BLE driver.  
    The server uses UDP port number 5000 by default. Please check any other app is already using the port or not.  

### 5. Measuring Performance.

- First, connect to the RPiAP Wi-Fi AP.

![connect_wifi](/images/connect.png)

- In andorid application, you first select a file to send.

![choose_file](/images/chhose_file.png)

- Click send button to measure the transmission performance.  
You can see the current simulation setup and the transmission status.

![transfer](/images/transfer.png)

### 6. Sample Result

![performance](/images/performance.png)

![performance](/images/performance2.png)

![performance](/images/performance3.png)

![performance](/images/performance4.png)

![performance](/images/performance5.png)
