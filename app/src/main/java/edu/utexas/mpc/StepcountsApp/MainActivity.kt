package edu.utexas.mpc.StepcountsApp

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.*
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.util.*
import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.*
import android.text.InputType


class MainActivity : AppCompatActivity() {


    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var textView: TextView
    lateinit var phonenum: EditText
    lateinit var smstext: EditText
    lateinit var imageView: ImageView
    lateinit var retrieveButton: Button
    lateinit var switchwifiButton: Button
    lateinit var goalcheckinButton: Button
    lateinit var smsButton: Button
    //lateinit var btnok: Button

    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var mostRecentWeatherForecast: ForeCastWeatherResult
    lateinit var mqttAndroidClient: MqttAndroidClient
    lateinit var todaysDate: String
    lateinit var onmessageflag: String


    // you may need to change this depending on where your MQTT broker is running
    val serverUri = "tcp://192.168.4.14:1883"
    // you can use whatever name you want to here
    val clientId = "EmergingTechMQTTClient"

    //these should "match" the topics on the "other side" (i.e, on the Raspberry Pi)
    val subscribeTopic = "testTopic2"
    val publishTopic = "testTopic1"

    var weatherData  = ""
    var weatherForecast  = ""
    //var onmessageflag = "False"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = this.findViewById(R.id.text)
        retrieveButton = this.findViewById(R.id.retrieveButton)
        switchwifiButton = this.findViewById(R.id.switchwifiButton)
        goalcheckinButton = this.findViewById(R.id.goalcheckinButton)
        smsButton = this.findViewById(R.id.smsButton)
        phonenum = findViewById<EditText>(R.id.phonenum)
        smstext = findViewById<EditText>(R.id.smstext)

        // keep goal /switch wifi/sms button invisible on create
        switchwifiButton.setVisibility(View.INVISIBLE)
        goalcheckinButton.setVisibility(View.INVISIBLE)
        smsButton.setVisibility(View.INVISIBLE)
        phonenum.setVisibility(View.INVISIBLE)
        smstext.setVisibility(View.INVISIBLE)

        // check permissions for sending text
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)!= PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.SEND_SMS),111)
        }
        //else
        //    receiveMsg()

        // text button actions
        smsButton.setOnClickListener({ sendsms() })

        // other button actions
        switchwifiButton.setOnClickListener({ switchwifi() })
        goalcheckinButton.setOnClickListener({ syncWithPi() })
        retrieveButton.setOnClickListener({ requestWeather() })

        queue = Volley.newRequestQueue(this)
        gson = Gson()

        // initialize the paho mqtt client with the uri and client id
        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                // this subscribes the client to the subscribe topic
                mqttAndroidClient.subscribe(subscribeTopic, 0)
                val message = MqttMessage()

                message.payload = (weatherData + " " + weatherForecast).toByteArray()
                println("+++ before publish...")
                println(message)
                println(publishTopic)

                // this publishes a message to the publish topic
                mqttAndroidClient.publish(publishTopic, message)
            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println(message)
                textView.text = message.toString()
                retrieveButton.setVisibility(View.VISIBLE)
                onmessageflag = "True"
                println("????????????")
                println(onmessageflag)
                if(textView.text.contains("Goal"))
                {
                    if (onmessageflag.equals("True") )
                    {
                        println("###########")
                        println(onmessageflag)
                        onmessageflag = "False"
                        println("***********")
                        println(onmessageflag)
                        smsdialog()
                    }
                }

            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })
    }

    // validating permission
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode==111 && grantResults[0]==PackageManager.PERMISSION_GRANTED)
            receiveMsg()
    }

    // this method is used to get current day's weather
    fun requestWeather(){
        //Sending stuff to PI
        println("requesting weather")
        retrieveButton.setVisibility(View.INVISIBLE)
        val url = StringBuilder("https://api.openweathermap.org/data/2.5/weather?id=4254010&appid=ea9d4a9da52aaa9920a1f1ab27e0086e").toString()
        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
            com.android.volley.Response.Listener<String> { response ->
                //textView.text = response
                mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                textView.text = mostRecentWeatherResult.weather.get(0).main

                val icon: String = mostRecentWeatherResult.weather.get(0).icon

                val iconUrl = "https://openweathermap.org/img/w/$icon.png"
                //print icon in image view
                imageView = this.findViewById(R.id.image_view)
                Picasso.with(this).load(iconUrl).into(imageView)

                Log.v("request weather", url)

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd")
                val date = java.util.Date(mostRecentWeatherResult.dt *1000)
                todaysDate = sdf.format(date)

                weatherData = "WeatherData: "  +
                        " " +  todaysDate +
                        " " + mostRecentWeatherResult.main.temp_min.toString() +
                        " " + mostRecentWeatherResult.main.temp_max.toString() +
                        " " + mostRecentWeatherResult.main.humidity.toString()
                println(weatherData)

                requestForecast()

                switchwifiButton.setVisibility(View.VISIBLE)
                goalcheckinButton.setVisibility(View.INVISIBLE)

                val toast = Toast.makeText(this, "Click Switch wifi and follow steps to do Goal Check-in", Toast.LENGTH_LONG)
                val textView = toast.view.findViewById(android.R.id.message) as TextView
                textView.setTextColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
                toast.show()
            },
            com.android.volley.Response.ErrorListener { println("******request weather didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }

    // this method is used to get next day's forecast
    fun requestForecast(){
        //Sending stuff to PI
        val url_forecast = StringBuilder("https://api.openweathermap.org/data/2.5/forecast?id=524901&appid=ea9d4a9da52aaa9920a1f1ab27e0086e").toString()
        val forecastRequest = @RequiresApi(Build.VERSION_CODES.O)
        object : StringRequest(com.android.volley.Request.Method.GET, url_forecast,
            com.android.volley.Response.Listener<String> { response ->
                //textView.text = response
                mostRecentWeatherForecast = gson.fromJson(response, ForeCastWeatherResult::class.java)
                println("requestforecast")

                Log.v("request forecast", url_forecast)

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd")

                var forecast_date_counter = 0

                while (forecast_date_counter  < 9) {

                    val forecast_epoch_date = mostRecentWeatherForecast.list.get(forecast_date_counter).dt
                    val date = java.util.Date(forecast_epoch_date*1000)
                    val forecast_date = sdf.format(date)

                    if (forecast_date != todaysDate ) {
                        weatherForecast = "WeatherForecast: "  +
                                " " + forecast_date +
                                " " + mostRecentWeatherForecast.list.get(forecast_date_counter).main.temp_min.toString() +
                                " " + mostRecentWeatherForecast.list.get(forecast_date_counter).main.temp_max.toString() +
                                " " + mostRecentWeatherForecast.list.get(forecast_date_counter).main.humidity.toString()
                        println(weatherForecast)
                        break
                    }
                    forecast_date_counter ++
                }

            },

            com.android.volley.Response.ErrorListener { println("******forecast didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(forecastRequest)
    }


    // this method is used to alert user to switch wifi
    fun switchwifi(){
        println("+++++++ allowing user to switch wifi ...")
        var builder = AlertDialog.Builder(this )

        builder.setTitle(getString(R.string.switch_wifi))
        builder.setMessage(getString(R.string.switch_wifi_message))


        builder.setPositiveButton("ok", DialogInterface.OnClickListener{ dialog, id ->
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            dialog.dismiss()
        })
        builder.setOnDismissListener(DialogInterface.OnDismissListener() {
            adjustVisibility()

        })
        var alert = builder.create()
        alert.show()


    }

    // this method is used to confirm if user switched to wifi and present "Goal check in" button
    fun adjustVisibility(){
        println("+++++++ allowing user to confirm wifi switch ...")
        var builder = AlertDialog.Builder(this )
        builder.setTitle(getString(R.string.switched_wifi))
        builder.setMessage("Are you done switching wi fi")

        builder.setPositiveButton("yes", DialogInterface.OnClickListener{ dialog, id ->
            switchwifiButton.setVisibility(View.INVISIBLE)
            goalcheckinButton.setVisibility(View.VISIBLE)
//            smsButton.setVisibility(View.VISIBLE)
//            phonenum.setVisibility(View.VISIBLE)
//            smstext.setVisibility(View.VISIBLE)
            dialog.cancel()
        //    requestWeather()
        })
        builder.setNegativeButton(getString(R.string.no), DialogInterface.OnClickListener{dialog,id ->
            dialog.cancel()
        })
        var alert = builder.create()
        alert.show()
    }

    // this method is used to alert user to switch wifi
    fun smsdialog(){
        println("+++++++ allowing user to send text ...")
        var builder = AlertDialog.Builder(this )

        builder.setTitle("Send Text to friend")
        builder.setMessage("Would you like to share your success to your friend?")

        builder.setPositiveButton("yes", DialogInterface.OnClickListener{ dialog, id ->
            sendtext((this@MainActivity))
            dialog.dismiss()
        })
        builder.setNegativeButton(getString(R.string.no), DialogInterface.OnClickListener{dialog,id ->
            dialog.cancel()
        })
        var alert = builder.create()
        alert.show()

    }

    fun sendtext(activity: Activity){
        println("+++++++ allowing user to send text ...")
        val dialog = Dialog(activity)
        //dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_layout)

        var et2: EditText? = null
        var et3: EditText? = null
        var btnok: Button

        et2 = dialog.findViewById(R.id.et2)
        et3 = dialog.findViewById(R.id.et3)
        btnok = dialog.findViewById(R.id.btnok)

        //val btnok = dialog.findViewById(R.id.btnok) as Button
        btnok.setOnClickListener {
            println("send test button is working")
            var sms = SmsManager.getDefault()
            sms.sendTextMessage(et2.getText().toString(),"Group4",et3.getText().toString(),null,null)
            dialog.dismiss()
        }

        dialog.show()

    }

    // this method is used to set up MQTT connection
    fun syncWithPi(){
        println("+++++++ Setting MQTT connection ...")
        mqttAndroidClient.connect()
        //switchwifi()
        //retrieveButton.setVisibility(View.VISIBLE)
        //textView.setText("")
        //imageView.setImageBitmap(null)
    }

    fun sendsms(){
        println("+++++++ send sms ...")
        println(phonenum)
        var sms = SmsManager.getDefault()
        sms.sendTextMessage(phonenum.text.toString(),"Group4",smstext.text.toString(),null,null)
    }

    private fun receiveMsg(){
        println("+++++++ receive sms ...")
        var br = object: BroadcastReceiver(){
            override fun onReceive(p0: Context?, p1: Intent?) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
                    for(sms in Telephony.Sms.Intents.getMessagesFromIntent(p1)){
                        Toast.makeText(applicationContext,sms.displayMessageBody,Toast.LENGTH_LONG).show()
                        phonenum.setText(sms.originatingAddress)
                        smstext.setText(sms.displayMessageBody)
                    }
                }
            }
        }
        registerReceiver(br, IntentFilter("android.provider.Telephony.SMS_RECEIVED"))
    }


}

class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val dt: Long,val main: WeatherMain,val weather: Array<Weather>  )
class ForeCastWeatherResult(val list: Array<Forecast>)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class Forecast(val dt: Long, val main: WeatherMain)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)
