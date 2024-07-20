package feicheiel.main.fieneryc

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NoLiveLiterals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.opencsv.CSVWriter
import feicheiel.main.fieneryc.ui.theme.FienerycTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.KMutableProperty0

val neueFontFamily = FontFamily(
    Font(R.font.black, FontWeight.Black),
    Font(R.font.light, FontWeight.Light),
    Font(R.font.regular, FontWeight.Normal)
)

// DEFINITION OF CONSTANTS
enum class BluetoothState {
     CONNECTED,
     DISCONNECTED,
     OFF
}

enum class LocationState {
    ENABLED,
    DISABLED
}

object BroadcastActions {
    const val ACTION_UPDATE_UI_STATE = "feicheiel.main.fieneryc.ACTION_UPDATE_UI_STATE"
}

data class UIState (
    var signalStrength: Int = 0,
    var energyConsumption: Double = 0.0,
    var isConsumptionHigh: Boolean = false,
    var consumptionUnit: String = "",
    var switchStates: List<Boolean> = listOf(false, false, false),
    var rcv: String = ""
)

class BluetoothManager (
    var inputStream: InputStream? = null,
    var outputStream: OutputStream? = null,
    var context: Context? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendChannel = Channel<String>()

    private suspend fun readFromDevice() {
        try {
            var sRead: Char = ' '
            var stringSRead: String = ""

            while (true) {
                // Read from the InputStream
                withContext(Dispatchers.IO){

                    // Reader
                    var countComma = 0
                    while(inputStream?.available()!! > 0) {
                        sRead = inputStream?.read()!!.toChar()
                        when (sRead) {
                            '+' -> {
                                stringSRead = ""; countComma = 0
                                stringSRead += sRead
                            }
                            ',' -> {
                                countComma+=1
                                stringSRead += sRead
                            }
                            '\r', '\n' -> {
                                sRead = ' '
                                stringSRead += sRead
                            }
                            else -> {
                                stringSRead += sRead
                            }
                        }
                    }
                    // Send broadcast
                    if (countComma == 8 && stringSRead[0] == '+'){
                        println(stringSRead)
                        withContext(Dispatchers.Main){
                            context?.let { sendUIStateBroadcast(it, stringSRead) }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private suspend fun sendDataToDevice() {
        for (message in sendChannel) {
            try {
                withContext(Dispatchers.IO) {
                    outputStream?.write(message.toByteArray())
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun sendMessage(message: String) {
        scope.launch {
            sendChannel.send(message)
        }
    }

    fun close() {
        scope.cancel()
        try {
            inputStream?.close()
            outputStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    public fun initializeInputStream(isref: KMutableProperty0<InputStream?>, realIS: InputStream?){
        isref.set(realIS)
        if (inputStream != null){
            scope.launch {
                // Start Coroutine to read from the device
                readFromDevice()
            }
        }
    }

    public fun initializeOutputStream(osref: KMutableProperty0<OutputStream?>, realIS: OutputStream?){
        osref.set(realIS)
        if (outputStream != null){
            scope.launch {
                // Start the Coroutine to send data to the device
                sendDataToDevice()
            }
        }
    }

    public fun initializeContext(contextref: KMutableProperty0<Context?>, realIS: Context?){
        contextref.set(realIS)
    }
}

class MainActivity : ComponentActivity() {
    private val bluetoothManager = BluetoothManager()
    val bmInputStreamProperty: KMutableProperty0<InputStream?> = bluetoothManager::inputStream
    val bmOutputStreamProperty: KMutableProperty0<OutputStream?> = bluetoothManager::outputStream
    val bmContextProperty: KMutableProperty0<Context?> = bluetoothManager::context
    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            // ToDo: Handle the case where permissions are not granted
            requestBluetoothPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            FienerycTheme {
                StartThisApp(bluetoothManager, isProperty = bmInputStreamProperty, osProperty = bmOutputStreamProperty, cnProperty = bmContextProperty)
            }
        }

        requestBluetoothPermissions()
    }

    private fun requestBluetoothPermissions() {
        requestBluetoothPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.close()
    }
}

@Composable
fun StartThisApp(bluetoothManager: BluetoothManager,
                 isProperty: KMutableProperty0<InputStream?>,
                 osProperty: KMutableProperty0<OutputStream?>,
                 cnProperty: KMutableProperty0<Context?>
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController)}
        composable("connect") { ConnectScreen(navController, cnProperty=cnProperty, osProperty = osProperty, isProperty = isProperty, bm = bluetoothManager) }
        composable("data") { LiveDataScreen(bluetoothManager, navController) }
    }
}

// BROADCAST RECEIVERS
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    private val _bluetoothState = MutableLiveData<BluetoothState>()
    val bluetoothState: LiveData<BluetoothState> = _bluetoothState

    private val bluetoothStateReceiver = BluetoothStateReceiver {state ->
        _bluetoothState.postValue(state)
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        application.registerReceiver(bluetoothStateReceiver, filter)
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(bluetoothStateReceiver)
    }

    class BluetoothStateReceiver(
        private val onBluetoothStateChanged: (BluetoothState) -> Unit
    ) : BroadcastReceiver() {
        // ToDo: rectify the receiver to respond accurately to bluetooth state changes.
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val isBluetoothOn = state == BluetoothAdapter.STATE_ON
                    if (isBluetoothOn) onBluetoothStateChanged(BluetoothState.DISCONNECTED) else onBluetoothStateChanged(BluetoothState.OFF)
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR)
                    val isBluetoothConnected = state == BluetoothAdapter.STATE_CONNECTED
                    if (isBluetoothConnected) onBluetoothStateChanged(BluetoothState.CONNECTED) else onBluetoothStateChanged(BluetoothState.DISCONNECTED)
                }
            }
        }
    }
}

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val _locationState = MutableLiveData<LocationState>()
    val locationState: LiveData<LocationState> = _locationState

    init {
        val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        _locationState.value = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            LocationState.ENABLED
        } else {
            LocationState.DISABLED
        }

        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        application.registerReceiver(LocationStateReceiver {state ->
            _locationState.postValue(state)
        }, filter)
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(LocationStateReceiver {})
    }

    class LocationStateReceiver(
        private val onLocationStateChanged: (LocationState) -> Unit
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                if (isLocationEnabled) onLocationStateChanged(LocationState.ENABLED) else onLocationStateChanged(LocationState.DISABLED)
            }
        }
    }
}

// STATE VARIABLES TO PARSE RECEIVED INFORMATION.
@NoLiveLiterals
private fun parseData(data: String): UIState {
    // +RCV=11731,8,1312,u,1,0,1,-99,-40
    // <send_addr>,<payload_bytes>,<en>,<unit>,<s1>,<l>,<s2>,<rssi>,<snr>
    // 0,           1,              2,   3,     4,   5,  6,   7,     8
    val parsedData = UIState(0,0.0,false,"",listOf(false, false, false))
    val dataSplit = data.split(",")
    if (dataSplit.size < 9) {
        //means incomplete data read
    } else {
        parsedData.energyConsumption = dataSplit[2].toDouble()
        parsedData.signalStrength = dataSplit[7].toInt()
        parsedData.consumptionUnit = when (dataSplit[3]) {
            "u" -> "Wh"
            "k" -> "kWh"
            else -> {""}
        }
        parsedData.switchStates = listOf(
            (dataSplit[4] == "1"), (dataSplit[5] == "1"), (dataSplit[6] == "1")
        )
        parsedData.isConsumptionHigh = if ((parsedData.energyConsumption > 150.0) && (parsedData.consumptionUnit == "kWh")) true else false
        parsedData.rcv = data
    }
    return parsedData
}

fun sendUIStateBroadcast(context: Context, data: String) {
    val uiState = parseData(data)
    val intent = Intent(BroadcastActions.ACTION_UPDATE_UI_STATE).apply {
        putExtra("signalStrength", uiState.signalStrength)
        putExtra("energyConsumption", uiState.energyConsumption)
        putExtra("isConsumptionHigh", uiState.isConsumptionHigh)
        putExtra("consumptionUnit", uiState.consumptionUnit)
        putExtra("switchStates", uiState.switchStates.toBooleanArray())
        putExtra("rcv", uiState.rcv)
    }
    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
}

class UIStateViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableLiveData(UIState())
    val uiState: LiveData<UIState> = _uiState

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BroadcastActions.ACTION_UPDATE_UI_STATE -> {
                    val signalStrength = intent.getIntExtra("signalStrength", 0)
                    val energyConsumption = intent.getDoubleExtra("energyConsumption", 0.0)
                    val consumptionUnit = intent.getStringExtra("consumptionUint") ?: ""
                    val isConsumptionHigh = intent.getBooleanExtra("isConsumptionHigh", false)
                    val switchStates = intent.getBooleanArrayExtra("switchStates")?.toList() ?: listOf(false, false, false)
                    val rcv = intent.getStringExtra("rcv") ?: ""
                    _uiState.value = UIState(signalStrength, energyConsumption, isConsumptionHigh, consumptionUnit, switchStates, rcv)
                }
            }
        }
    }

    init{
        val filter = IntentFilter(BroadcastActions.ACTION_UPDATE_UI_STATE)
        LocalBroadcastManager.getInstance(application).registerReceiver(broadcastReceiver, filter)
    }

    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(broadcastReceiver)
    }
 }

//SCREENS.
@Composable
fun SplashScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
){
    val bckgSplashScreen = painterResource(id = R.drawable.bck_splash_scr)
    val systemUiController = rememberSystemUiController()
    systemUiController.isNavigationBarVisible = false
    systemUiController.setStatusBarColor(Color.Transparent)

    var visible by remember { mutableStateOf(true) }
    //val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, label = "")

    LaunchedEffect(Unit) {
        delay(1500)
        visible = false
        delay(200)
        navController.navigate("connect") {
            popUpTo("splash") {inclusive = true}
        }
    }
    Box (modifier.fillMaxSize()) {
        Image(
            painter = bckgSplashScreen,
            contentDescription = "A beautiful resting ocean shore",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxSize()
        )
        Column(modifier.align(Alignment.BottomCenter)) {
            AppName(Modifier.absoluteOffset(97.dp, (-217).dp))
            DeveloperSignature(
                name = stringResource(id = R.string.company_name),
                modifier
            )
        }
    }
}

@Composable
fun ConnectScreen(
    navController: NavHostController,
    bm: BluetoothManager,
    isProperty: KMutableProperty0<InputStream?>,
    osProperty: KMutableProperty0<OutputStream?>,
    cnProperty: KMutableProperty0<Context?>,
    modifier: Modifier = Modifier
) {
    val bckConnectScreen = painterResource(id = R.drawable.bck_conn_scr)

    var showDialog by remember { mutableStateOf(false) }
    var bluetoothEnabled by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null)}
    var hasPermissions by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothEnabled = bluetoothAdapter?.isEnabled == true
        if (bluetoothEnabled) {
            showDialog = true
        }
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            hasPermissions = true
            if (bluetoothAdapter?.isEnabled == false) {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            // ToDo: if location turned off, request that it be turned on.
        }
    }

    LaunchedEffect(Unit) {
        val permissions =
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            )
        requestPermissionsLauncher.launch(permissions)
    }

    Box(modifier.fillMaxSize()){
        Image(
            painter = bckConnectScreen,
            contentDescription = "A man holds his phone to connect to a smart device attached to the wall",
            contentScale = ContentScale.FillBounds
        )
        Text(
            text = "Let's begin by first connecting you to your Smart Meter. \nPlease press the Connect button below \nSelect the name of your meter from the list of devices",
            style = TextStyle(
                fontFamily = neueFontFamily,
                fontWeight = FontWeight.Normal
            ),
            fontSize = 17.sp, lineHeight = 23.sp,
            color = Color(0xFF226A67),
            modifier = Modifier
                .fillMaxWidth()
                .offset(3.dp, 377.dp)
                .padding(3.dp)
        )
        Button(
            onClick = {
                if (bluetoothAdapter?.isEnabled==true) {
                    showDialog = true
                } else {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    //showDialog = true
                }
            },
            modifier = modifier
                .offset(97.dp, 477.dp)
                .size(197.dp, 67.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF247409), Color(0xFF001800)),
                    ),
                    shape = RoundedCornerShape(37.dp)
                ),
            colors = ButtonColors(
                contentColor = Color.Transparent,
                containerColor = Color.Transparent,
                disabledContentColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            )
        ) {
            Text(
                text = "Connect",
                style = TextStyle(fontFamily = neueFontFamily, fontWeight = FontWeight.Black),
                fontSize = 23.sp,
                color = Color.White,
                modifier = modifier.background(color = Color.Transparent)
            )
        }

        if (showDialog) {
            BluetoothDevicesDialog(
                pairedDevices = pairedDevices.orEmpty().toList(),
                onDismiss = { showDialog = false },
                onDeviceSelected = {device ->
                    selectedDevice = device
                    showDialog = false
                    connectToDevice(context, device, onConnectionResult = { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        if (success) {
                            navController.navigate("data")
                        }
                    }, bm, isProperty, osProperty, cnProperty)
                }
            )
        }
    }
}

@Composable
fun LiveDataScreen(
    bluetoothManager: BluetoothManager,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    bluetoothViewModel: BluetoothViewModel = viewModel(),
    locationViewModel: LocationViewModel = viewModel(),
    uiStateViewModel: UIStateViewModel = viewModel()
) {
    val uiState by uiStateViewModel.uiState.observeAsState(UIState())
    var isFullScreen by remember { mutableStateOf(true) }
    val activity = LocalContext.current as Activity

    fun enterFullScreen(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.decorView.systemUiVisibility = View.KEEP_SCREEN_ON or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_LAYOUT_FLAGS or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    fun exitFullScreen(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    LaunchedEffect(Unit) {
        enterFullScreen(activity)
    }

    fun toggleFullScreen() {
        if (isFullScreen) {
            exitFullScreen(activity)
        } else {
            enterFullScreen(activity)
        }
        isFullScreen = !isFullScreen
    }

    // STATE VARIABLES
    // 1. Bluetooth
    val imgBluetoothOn = painterResource(id = R.drawable.bluetooth)
    val imgBluetoothOff = painterResource(id = R.drawable.bluetooth_off)
    val imgBluetoothConnected = painterResource(id = R.drawable.bluetooth_connected)
    val bluetoothState by bluetoothViewModel.bluetoothState.observeAsState(BluetoothState.CONNECTED)
    val imgBluetoothState: Painter = when (bluetoothState) {
        BluetoothState.OFF -> imgBluetoothOff
        BluetoothState.CONNECTED -> imgBluetoothConnected
        BluetoothState.DISCONNECTED -> imgBluetoothOn
    }
    if (bluetoothState != BluetoothState.CONNECTED) {
        bluetoothManager.close()
        navController.navigate("connect")
    }
    // 2. Location
    val imgLocationOn = painterResource(id = R.drawable.location_on)
    val imgLocationOff = painterResource(id = R.drawable.location_off)
    val locationState by locationViewModel.locationState.observeAsState(LocationState.DISABLED)
    val imgLocation: Painter = when (locationState) {
        LocationState.ENABLED -> imgLocationOn
        LocationState.DISABLED -> imgLocationOff
    }
    // 3. Signal Strength.
    val imgNetwork0 = painterResource(id = R.drawable.ntwk_0)
    val imgNetwork1 = painterResource(id = R.drawable.ntwk_1)
    val imgNetwork2 = painterResource(id = R.drawable.ntwk_2)
    val imgNetwork3 = painterResource(id = R.drawable.ntwk_3)
    val imgNetwork: Painter = if ( uiState.signalStrength <= -80) imgNetwork1
                           else if (uiState.signalStrength <= -67) imgNetwork2
                           else if ( uiState.signalStrength <= -30) imgNetwork3
                           else imgNetwork0
    // log signal strength to CSV.
    val context = LocalContext.current
    logDataToCSV(context, uiState.signalStrength.toString()) {_, msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // 4. Ring Colour & Alert Indicator.
    val imgRingRed = painterResource(id = R.drawable.red_ring)
    val imgRingGreen = painterResource(id = R.drawable.green_ring)
    val imgRing: Painter = if (uiState.isConsumptionHigh) imgRingRed else imgRingGreen
    val imgBallRed = painterResource(id = R.drawable.red_ball)
    val imgBallGreen = painterResource(id = R.drawable.green_ball)
    val imgBallConsume: Painter = if (uiState.isConsumptionHigh) imgBallRed else imgBallGreen

    val imgBallSensors: Painter = if ((locationState == LocationState.ENABLED) && (bluetoothState == BluetoothState.CONNECTED)) imgBallGreen else imgBallRed


    // 5. Switch States Inidicator.
    val imgLightOn = painterResource(id = R.drawable.light_on)
    val imgLightOff = painterResource(id = R.drawable.light_off)
    val imgSwitchOn = painterResource(id = R.drawable.toggle_on)
    val imgSwitchOff = painterResource(id = R.drawable.toggle_off)
    val imgSwitch1: Painter = if (uiState.switchStates[0]) imgSwitchOn else imgSwitchOff
    val imgSwitch2: Painter = if (uiState.switchStates[2]) imgSwitchOn else imgSwitchOff
    val imgLight: Painter = if (uiState.switchStates[1]) imgLightOn else imgLightOff

    // UI Background File.
    val bckLiveData = painterResource(id = R.drawable.bck_live_data_scr)

    // Draw the UI
    Box (
        modifier
            .fillMaxSize()
            .alpha(
                when (bluetoothState) {
                    BluetoothState.CONNECTED -> 1.0f
                    BluetoothState.OFF -> 0.3f
                    BluetoothState.DISCONNECTED -> 0.7f
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        toggleFullScreen()
                    }
                )
            }
    ){
        Image(
            painter = bckLiveData,
            contentDescription = "This is a nice background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Column(
            modifier
                .fillMaxSize()
                .padding(10.dp)){
            // status indicator.
            Row (modifier = Modifier
                .align(Alignment.End)
                .offset(y = 33.dp)
            ){
                Image(
                    painter = imgBluetoothState,
                    contentDescription = "Bluetooth Status indicator: ON, OFF, or CONNECTED",
                    modifier = Modifier.size(17.dp)
                )
                Image(
                    painter = imgLocation,
                    contentDescription = when (locationState) {
                        LocationState.DISABLED -> "Location Disabled"
                        LocationState.ENABLED -> "Location Enabled"
                    },
                    modifier = Modifier.size(17.dp)
                )
                Image(
                    painter = imgNetwork,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Image(
                    painter = imgBallSensors,
                    contentDescription = null,
                    modifier = Modifier
                        .size(3.dp)
                        .offset(0.dp, 7.dp)
                )
            }

            // energy consumption.
            Box(
                modifier = Modifier
                    .offset(31.dp, 270.dp)
                    .size(307.dp)
            ){
                Image(painter = imgRing, contentDescription = null)
                Text (
                    text = uiState.energyConsumption.toString(),
                    fontFamily = neueFontFamily,
                    fontSize = 77.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.offset(77.dp, 117.dp)
                )
                Image(
                    painter = imgBallConsume,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(300.dp, 37.dp)
                        .size(5.dp)
                )
                Text (
                    text = uiState.consumptionUnit,
                    fontFamily = neueFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.offset(270.dp, 33.dp)
                )
            }
            // socket state indicator.
            Row(modifier = Modifier.offset(113.dp, 307.dp)) {
                Image( //Switch 1
                    painter = imgSwitch1,
                    contentDescription = null,
                    modifier = Modifier
                        .size(43.dp)
                        .offset(0.dp, 17.dp)
                        .clickable {
                            bluetoothManager.sendMessage("A${if (uiState.switchStates[0]) 1 else 0}, ")
                        }
                )
                Image( //Light
                    painter = imgLight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(73.dp)
                        .clickable {
                            bluetoothManager.sendMessage("L${if (uiState.switchStates[1]) 1 else 0}, ")
                        }
                )
                Image( // Switch 2
                    painter = imgSwitch2,
                    contentDescription = null,
                    modifier = Modifier
                        .size(43.dp)
                        .offset(0.dp, 17.dp)
                        .clickable {
                            bluetoothManager.sendMessage("B${if (uiState.switchStates[2]) 1 else 0}, ")
                        }
                )
            }

            Text(text = uiState.rcv,
                modifier = Modifier.absoluteOffset((-9).dp,433.dp),
                color = Color.Blue, fontSize = 10.sp,
                fontFamily = neueFontFamily, fontWeight = FontWeight.Black
            )
        }
    }


}

@Preview(showBackground = true)
@Composable
fun PreviewLiveDataScreen() {
    //LiveDataScreen(BluetoothManager())
}

//ALERT DIALOG BOX
/*@Composable
fun BluetoothDevicesDialog(pairedDevices: List<BluetoothDevice>, onDismiss: () -> Unit, onDeviceSelected: (BluetoothDevice) -> Unit) {
    // Implements a Glassmorphism dialog box.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .size(300.dp)
                .background(Color.White.copy(alpha = 0.1f))
                .blur(radius = 16.dp)
                .padding(16.dp),
            //elevation = 7.dp
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select a bluetooth device",
                    style = TextStyle(
                        fontFamily = neueFontFamily,
                        fontWeight = FontWeight.Black
                    ), fontSize = 23.sp, color = Color.White
                )

                Spacer(modifier = Modifier.padding(10.dp))

                Column {
                    pairedDevices.forEach{ device ->
                        Column(modifier = Modifier.clickable {
                            onDeviceSelected(device)
                        }){
                            Text(
                                text = device.name,
                                style = TextStyle(
                                    fontFamily = neueFontFamily,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = Color.LightGray,
                                fontSize = 17.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                            )

                            Text(
                                text = device.address,
                                style = TextStyle(
                                    fontFamily = neueFontFamily,
                                    fontWeight = FontWeight.Light
                                ),
                                color = Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .padding(vertical = 1.dp)
                            )

                            Spacer(modifier = Modifier.padding(5.dp))
                        }
                    }
                }

                Button (
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(107.dp, 53.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFB91A00), Color(0xFF4A1800)),
                            ),
                            shape = RoundedCornerShape(17.dp)
                        )
                        .padding(3.dp),
                        colors = ButtonColors(
                            contentColor = Color.Transparent,
                            containerColor = Color.Transparent,
                            disabledContentColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        )
                ) {
                    Text(
                        text="Close",
                        style = TextStyle(
                            fontFamily = neueFontFamily,
                            fontWeight = FontWeight.Normal
                        ),
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}*/

//Auxiliary Functions.
fun connectToDevice(
    context: Context,
    device: BluetoothDevice,
    onConnectionResult: (Boolean, String) -> Unit,
    bluetoothManager: BluetoothManager,
    isProperty: KMutableProperty0<InputStream?>,
    osProperty: KMutableProperty0<OutputStream?>,
    contextProperty: KMutableProperty0<Context?>,
) {
    val uuid = device.uuids[0].uuid
    val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
    try {
        socket.connect()
        onConnectionResult(true, "Successfully connected to ${device.name}")

        bluetoothManager.initializeContext(contextProperty, context)
        bluetoothManager.initializeInputStream(isProperty, socket.inputStream)
        bluetoothManager.initializeOutputStream(osProperty, socket.outputStream)
    } catch (e: IOException) {
        e.printStackTrace()
        onConnectionResult(false, "Connection to ${device.name} failed")
        try {
            socket.close()
        } catch (c: IOException){
            c.printStackTrace()
        }
    }
}

@Composable
fun BluetoothDevicesDialog(
    pairedDevices: List<BluetoothDevice>,
    onDismiss: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select a bluetooth device",
                style = TextStyle(
                    fontFamily = neueFontFamily,
                    fontWeight = FontWeight.Black
                ), fontSize = 23.sp, color = Color.White
            )
                },
        text = {
            Column {
                pairedDevices.forEach{ device ->
                    Column(modifier = Modifier.clickable {
                        onDeviceSelected(device)
                    }){
                        Text(
                            text = device.name,
                            style = TextStyle(
                                fontFamily = neueFontFamily,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.LightGray,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        )

                        Text(
                            text = device.address,
                            style = TextStyle(
                                fontFamily = neueFontFamily,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(vertical = 1.dp)
                        )
                        
                        Spacer(modifier = Modifier.padding(5.dp))
                    }
                }
            }
        },
        containerColor = Color(0xFE226A67),
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .size(107.dp, 53.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFB91A00), Color(0xFF4A1800)),
                        ),
                        shape = RoundedCornerShape(17.dp)
                    )
                    .padding(3.dp),
                colors = ButtonColors(
                    contentColor = Color.Transparent,
                    containerColor = Color.Transparent,
                    disabledContentColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                )
            ) {
                Text(
                    text="Close",
                    style = TextStyle(
                        fontFamily = neueFontFamily,
                        fontWeight = FontWeight.Normal
                    ),
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    )

}

@Composable
fun DeveloperSignature(
    name: String,
    modifier: Modifier = Modifier) {
    val downbar = painterResource(id = R.drawable.downbar)
    Column (
        verticalArrangement = Arrangement.Center,
        modifier = modifier.wrapContentSize(Alignment.BottomCenter)
    ) {
        Text(
            text = name,
            fontSize = 10.sp,
            lineHeight = 7.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = neueFontFamily,
                fontWeight = FontWeight.Normal
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Image(
            painter = downbar,
            contentDescription = "A golden coloured bar placed under the developer's name",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.size(173.dp,9.dp)
        )
    }
}

@Composable
fun AppName(modifier: Modifier = Modifier) {
    Column(modifier.wrapContentSize(Alignment.CenterEnd)) {
        Text(
            text = "FIENERYC",
            style = TextStyle(fontFamily = neueFontFamily, fontWeight = FontWeight.Black),
            fontSize = 33.sp,
            lineHeight = 3.sp,
            modifier = Modifier.align(Alignment.End)
        )
        Text(
            text = "Effortless, hassle-free energy management.",
            style = TextStyle(fontFamily = neueFontFamily, fontWeight = FontWeight.Light),
            fontSize = 13.sp,
            lineHeight = 2.sp,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@SuppressLint("MissingPermission")
fun logDataToCSV(
    context: Context,
    data: String,
    onResult: (Boolean, String) -> Unit
) {
    val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            val latitude = location.latitude
            val longitude = location.longitude
            writeDataToCSV(context, data, latitude, longitude, onResult)
        } else {
            onResult(false, "Failed to get location")
        }
    }.addOnFailureListener{
        onResult(false, "Failed to get location: ${it.message}")
    }
}

fun writeDataToCSV(
    context: Context,
    data: String,
    latitude: Double,
    longitude: Double,
    onResult: (Boolean, String) -> Unit
) {
    try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = dateFormat.format(Date())
        val fileName = "fieneryc-$date.csv"

        val file = File(context.filesDir, fileName)
        val writer = if (file.exists()) {
            CSVWriter(FileWriter(file, true))
        } else {
            CSVWriter(FileWriter(file))
        }

        val csvData = arrayOf(data, latitude.toString(), longitude.toString())
        writer.writeNext(csvData)
        writer.close()

        onResult(true, "Data logged Successfully to file: $fileName")
    } catch (e: IOException) {
        e.printStackTrace()
        onResult(false, "Failed to log data: ${e.message}")
    }
}