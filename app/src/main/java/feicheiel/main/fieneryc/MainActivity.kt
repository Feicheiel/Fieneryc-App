package feicheiel.main.fieneryc

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import feicheiel.main.fieneryc.ui.theme.FienerycTheme
import kotlinx.coroutines.delay
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Environment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.opencsv.CSVWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val neueFontFamily = FontFamily(
    Font(R.font.black, FontWeight.Black),
    Font(R.font.light, FontWeight.Light),
    Font(R.font.regular, FontWeight.Normal)
)

class MainActivity : ComponentActivity() {
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
        setContent {
            FienerycTheme {
                StartThisApp()
            }
        }
        requestBluetoothPermissions()
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADMIN
                )
            )
        }
    }
}

@Composable
fun StartThisApp() {
    val navController = rememberNavController()
    val receivedData by remember { mutableStateOf("") }
    

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController)}
        composable("connect") { ConnectScreen(navController) }
        composable("data") { LiveDataScreen(receivedData) }
    }
}

//SCREENS.
@Composable
fun SplashScreen(navController: NavHostController, modifier: Modifier = Modifier){
    val bckgSplashScreen = painterResource(id = R.drawable.bck_splash_scr)
    val systemUiController = rememberSystemUiController()
    systemUiController.isNavigationBarVisible = false
    systemUiController.setStatusBarColor(Color.Transparent)

    var visible by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, label = "")

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
            AppName(Modifier.absoluteOffset(97.dp,-217.dp))
            DeveloperSignature(
                name = stringResource(id = R.string.company_name),
                modifier
            )
        }
    }
}

@Composable
fun ConnectScreen(navController: NavHostController, modifier: Modifier = Modifier) {
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
            //showDialog = true
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
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        requestPermissionsLauncher.launch(permissions)
    }

    Box(modifier.fillMaxSize()){
        Image(
            painter = bckConnectScreen,
            contentDescription = "A man holds his phone to connect to a smart device attached to the wall",
            contentScale = ContentScale.FillBounds,
            alpha = 0.3f
        )
        Text(
            text = "Let's begin by first connecting you to your Smart Meter. \n- Please press the Connect button below \n- Select the name of your meter from the list of devices",
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
                    connectToDevice(context, device, onConnectionResult = {success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        if (success) {
                            // ToDo: open live data display screen.
                            navController.navigate("data")
                        }
                    }, onReceiveData =  {data ->
                        // TODO: update the UI with the received data.
                })
                }
            )
        }
    }
}

@Composable
fun LiveDataScreen(data: String, modifier: Modifier = Modifier, isBluetoothEnabled: Boolean = false, isBluetoothConnected: Boolean = false, isLocationEnabled: Boolean = false) {
    // Image Resources.
    val bckLiveData = painterResource(id = R.drawable.bck_live_data_scr)
    val bckDownbar = painterResource(id = R.drawable.downbar_grain)
    val imgBluetoothOn = painterResource(id = R.drawable.bluetooth)
    val imgBluetoothOff = painterResource(id = R.drawable.bluetooth_off)
    val imgBluetoothConnected = painterResource(id = R.drawable.bluetooth_connected)
    val imgLocationOn = painterResource(id = R.drawable.location_on)
    val imgLocationOff = painterResource(id = R.drawable.location_off)
    val imgNtwk_0 = painterResource(id = R.drawable.ntwk_0)
    val imgNtwk_1 = painterResource(id = R.drawable.ntwk_1)
    val imgNtwk_2 = painterResource(id = R.drawable.ntwk_2)
    val imgNtwk_3 = painterResource(id = R.drawable.ntwk_3)
    val imgLightOn = painterResource(id = R.drawable.light_on)
    val imgLightOff = painterResource(id = R.drawable.light_off)
    val imgSwitchOn = painterResource(id = R.drawable.toggle_on)
    val imgSwitchOff = painterResource(id = R.drawable.toggle_off)
    val imgRingRed = painterResource(id = R.drawable.red_ring)
    val imgRingGreen = painterResource(id = R.drawable.green_ring)
    val imgBallRed = painterResource(id = R.drawable.red_ball)
    val imgBallGreed = painterResource(id = R.drawable.green_ball)

    // State Variables.
    val bluetoothState by remember { mutableStateOf(isBluetoothEnabled) }
    val connectionState by remember { mutableStateOf(true) }
    val locationState by remember { mutableStateOf(isLocationEnabled) }

    // Draw the UI
    Box (modifier.fillMaxSize()){
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
            Row (modifier = Modifier.align(Alignment.End)){
                Image(
                    painter = if (connectionState) imgBluetoothConnected else if (bluetoothState) imgBluetoothOn else imgBluetoothOff,
                    contentDescription = "Bluetooth Status indicator: ON, OFF, or CONNECTED",
                    modifier = Modifier.size(17.dp)
                )
                Image(
                    painter = imgLocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Image(
                    painter = imgNtwk_2,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Image(
                    painter = imgBallGreed,
                    contentDescription = null,
                    modifier = Modifier.size(3.dp).offset(0.dp,7.dp)
                )
            }

            // energy consumption.
            Box(
                modifier = Modifier.offset(31.dp, 270.dp)
                    .size(307.dp)
            ){
                Image(painter = imgRingGreen, contentDescription = null)
                Text (
                    text = "143",
                    fontFamily = neueFontFamily,
                    fontSize = 77.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.offset(77.dp, 117.dp)
                )
                Image(
                    painter = imgBallGreed,
                    contentDescription = null,
                    modifier = Modifier.offset(300.dp, 37.dp).size(5.dp)
                )
                Text (
                    text = "Wh",
                    fontFamily = neueFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.offset(270.dp, 33.dp)
                )
            }
            // socket state indicator.
            Row(modifier = Modifier.offset(113.dp, 307.dp)) {
                Image( //Switch 1
                    painter = imgSwitchOn,
                    contentDescription = null,
                    modifier = Modifier.size(43.dp).offset(0.dp, 17.dp)
                )
                Image( //Light
                    painter = imgLightOn,
                    contentDescription = null,
                    modifier = Modifier.size(73.dp)
                )
                Image( // Switch 2
                    painter = imgSwitchOff,
                    contentDescription = null,
                    modifier = Modifier.size(43.dp).offset(0.dp, 17.dp)
                )
            }

            Text(text = data,
                modifier = Modifier.absoluteOffset(-9.dp,433.dp),
                color = Color.Blue, fontSize = 10.sp,
                fontFamily = neueFontFamily, fontWeight = FontWeight.Black
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLiveDataScreen() {
    LiveDataScreen(data = "jlk;kjl;jkl;0;1;1;89;87", isBluetoothConnected = true, isBluetoothEnabled = true, isLocationEnabled = true)
}

//ALERT DIALOG BOX
@Composable
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
}

//Auxiliary Functions.
fun connectToDevice(
    context: Context,
    device: BluetoothDevice,
    onConnectionResult: (Boolean, String) -> Unit,
    onReceiveData: (String) -> Unit
) {
    val uuid = device.uuids[0].uuid
    val socket: BluetoothSocket? = device.createRfcommSocketToServiceRecord(uuid)
    socket?.let{
        CoroutineScope(Dispatchers.IO).launch {
            try {
                it.connect()
                onConnectionResult(true, "Successfully connected to ${device.name}")

                // Start Listening for data in a loop
                val inputStream = it.inputStream
                val buffer = ByteArray(1024)
                while (true) {
                    try {
                        val bytesRead = inputStream.read(buffer)
                        val receivedData = String(buffer, 0, bytesRead)
                        withContext(Dispatchers.Main) {
                            //TODO: process the data received.
                            onReceiveData(receivedData)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        break
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onConnectionResult(
                        false,
                        "Failed to connect to ${device.name}. Please Try again!\n${e.cause} "
                    )
                }
                try {
                    it.close()
                } catch (closeException: IOException) {
                    closeException.printStackTrace()
                }
            }
        }
    }
}

/*@Composable
fun BluetoothDevicesDialog(pairedDevices: List<BluetoothDevice>, onDismissRequest: () -> Unit, onDeviceSelected: (BluetoothDevice) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
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
                onClick = onDismissRequest,
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

}*/

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
fun logDataToCSV(context: Context, data: String, onResult: (Boolean, String) -> Unit) {
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

fun writeDataToCSV(context: Context, data: String, latitude: Double, longitude: Double, onResult: (Boolean, String) -> Unit) {
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

        onResult(true, "Data logged Successfully")
    } catch (e: IOException) {
        e.printStackTrace()
        onResult(false, "Failed to log data: ${e.message}")
    }
}