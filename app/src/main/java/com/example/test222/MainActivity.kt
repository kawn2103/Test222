package com.example.test222

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android_serialport_api.SerialPort
import android_serialport_api.SerialPortFinder
import androidx.appcompat.app.AppCompatActivity
import com.example.test222.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    //시리얼 포트 연결
    var serialPort: SerialPort? = null              //시리얼 포트 객체
    var inputStream: FileInputStream? = null        //시리얼 데이터 인풋
    var outputStream: FileOutputStream? = null      //시리얼 데이터 아웃풋

    // 하드웨어의 장비 상태
    var operStatus: Byte? = null
    var modeOperStatus: Byte? = null
    var operMode: Byte? = null
    var workoutStatus: Byte? = null

    //기초 데이터 세팅
    var initFlag = false        //커넥트 플래그
    var playFlag = false        //운동 시작 플래그
    var nowLoad = 0.0           //현재 로드
    var nowSpeed = 0.0          //현재 속도
    var nowViscous = 0.0        //현재 점성 저항
    var nowStroke = 0.0         //현재 스토크
    var nowRepeat = 0.0         //반복 횟수
    var prePosition = 0.27      //이전 포지션
    var nowPosition = 0.0       //현재 포지션
    var totalDistance = 0.0     //총 운동 거리

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        //시리얼 포트 연결
        openSerialPort(SERIAL_PORT_NAME)
        //화면 세팅
        initView()
        //시리얼 데이터 리시브
        startRx()
        autoSend()
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        //연결 버튼 클릭
        binding.ConnectBt.setOnClickListener {
            if (!initFlag){ //연결 시작
                val arr = byteArrayOfInts(0x80) //커맨드 생성
                makeCommend(arr) //커맨드 전송
                //UI 변경
                binding.ConnectBt.text = "DisConnectBt!"
                binding.ConnectBt.setBackgroundColor(Color.parseColor("#FF0000"))
            }else{  //연결 종료
                val arr = byteArrayOfInts(0x02) //커맨드 생성
                makeCommend(arr) //커맨드 전송
                //UI 변경
                binding.ConnectBt.text = "ConnectBt!"
                binding.ConnectBt.setBackgroundColor(Color.parseColor("#00FF00"))
            }
            //플래그 변경
            initFlag = !initFlag
        }
        // 오퍼레이션 버튼 클릭
        binding.OperationalBt.setOnClickListener {
            val arr = byteArrayOfInts(0x01) //커맨드 생성
            makeCommend(arr) //커맨드 전송
        }
        //수동클라임 버튼 클릭
        binding.ClimberBt.setOnClickListener {
            // 수동 모드에 맞게 운동강도 초기화
            nowLoad = 5.0
            nowSpeed = 1.5
            nowViscous = 0.0
            nowStroke = 0.5
            nowRepeat = 0.0
            // UI 반영
            binding.loadTv.text = "Load : $nowLoad"
            binding.speedTv.text = "Speed : $nowSpeed"
            binding.viscousTv.text = "Viscous : $nowViscous"
            binding.strokeTv.text = "Stroke : $nowStroke"
            binding.repeatTv.text = "Repeat : $nowRepeat"

            val arr = byteArrayOfInts(0x10,0x01) //커맨드 생성
            makeCommend(arr) //커맨드 전송
        }
        //자동클라임 버튼 클릭
        binding.AutoClimberBt.setOnClickListener {
            //자동 모드에 맞게 운동강도 초기화
            nowLoad = 5.0
            nowSpeed = 0.3
            nowViscous = 0.0
            nowStroke = 0.2
            nowRepeat = 40.0

            // UI 반영
            binding.loadTv.text = "Load : $nowLoad"
            binding.speedTv.text = "Speed : $nowSpeed"
            binding.viscousTv.text = "Viscous : $nowViscous"
            binding.strokeTv.text = "Stroke : $nowStroke"
            binding.repeatTv.text = "Repeat : $nowRepeat"

            val arr = byteArrayOfInts(0x10,0x02) //커맨드 생성
            makeCommend(arr) //커맨드 전송
        }
        //운동 시작 버튼 클릭
        binding.StartBt.setOnClickListener {
            if (!playFlag){ //운동 시작
                val arr = byteArrayOfInts(0x11) //커맨드 생성
                makeCommend(arr) //커맨드 전송
                //UI 반영
                binding.StartBt.text = "StopBt!"
                binding.StartBt.setBackgroundColor(Color.parseColor("#FF0000"))
            }else{
                val arr = byteArrayOfInts(0x12) //커맨드 생성
                makeCommend(arr) //커맨드 전송
                //UI 반영
                binding.StartBt.text = "StartBt!"
                binding.StartBt.setBackgroundColor(Color.parseColor("#00FF00"))
                //운동 거리 초기화
                totalDistance = 0.0
                binding.distanceTv.text = "distance : $totalDistance 미터"
            }
            //플래그 변경
            playFlag = !playFlag
        }

        //넘버픽커 세팅
        binding.numberPicker.minValue = 1
        binding.numberPicker.maxValue = 5

        //운동강도 세팅
        binding.loadTv.text = "Load : $nowLoad"
        binding.speedTv.text = "Speed : $nowSpeed"
        binding.viscousTv.text = "Viscous : $nowViscous"
        binding.strokeTv.text = "Stroke : $nowStroke"
        binding.repeatTv.text = "Repeat : $nowRepeat"

        //더하기 버튼 클릭
        binding.plusBt.setOnClickListener {
            var result = 0.0            //계산 결과 값
            var status : Int? = null    //종류 스테이터스

            when(binding.radioGroup.checkedRadioButtonId){
                binding.loadBt.id -> { //로드 버튼
                    //현재 값에 넘버픽커에 있는 값 계산
                    nowLoad += binding.numberPicker.value
                    //최대 치 계산
                    if (nowLoad >50)
                        nowLoad = 50.0
                    nowLoad = round(nowLoad*10)/10  //실수 값을 소수점 한자리까지만 나오도록
                    binding.loadTv.text = "Load : $nowLoad" //UI반영
                    result = nowLoad*100 //결과 값 세팅
                    status = 0x01 //스테이더스 세팅
                }
                binding.speedBt.id -> { //스피드 버튼
                    when(workoutStatus){ //자동인지 수동인지 판별
                        0x01.toByte() -> { //수동일 때
                            //현재 값에 넘버픽커에 있는 값 계산
                            nowSpeed += (binding.numberPicker.value * 0.1)
                            //최대 치 계산
                            if (nowSpeed > 1.5)
                                nowSpeed = 1.5
                            nowSpeed = round(nowSpeed*10)/10 //실수 값을 소수점 한자리까지만 나오도록
                            binding.speedTv.text = "Speed : $nowSpeed" //UI반영
                            result = nowSpeed*1000 //결과 값 세팅
                        }
                        0x02.toByte() -> { //자동일 때
                            //현재 값에 넘버픽커에 있는 값 계산
                            nowSpeed += (binding.numberPicker.value * 0.1)
                            //최대 치 계산
                            if (nowSpeed > 0.8)
                                nowSpeed = 0.8
                            nowSpeed = round(nowSpeed*10)/10 //실수 값을 소수점 한자리까지만 나오도록
                            binding.speedTv.text = "Speed : $nowSpeed" //UI반영
                            result = nowSpeed*1000 //결과 값 세팅
                        }
                    }
                    status = 0x02 //스테이더스 세팅
                }
                binding.viscousBt.id -> { //점성강도 버튼
                    //현재 값에 넘버픽커에 있는 값 계산
                    nowViscous += (binding.numberPicker.value*10)
                    //최대 치 계산
                    if (nowViscous >1000)
                        nowViscous = 1000.0
                    nowViscous = round(nowViscous*10)/10 //실수 값을 소수점 한자리까지만 나오도록
                    binding.viscousTv.text = "Viscous : $nowViscous" //UI반영
                    result = nowViscous //결과 값 세팅
                    status = 0x03 //스테이더스 세팅
                }
                binding.strokeBt.id -> { //스트로크 버튼
                    when(workoutStatus){ //자동 수동 판결
                        0x01.toByte() -> { //수동
                            //현재 값에 넘버픽커에 있는 값 계산
                            nowStroke += (binding.numberPicker.value * 0.01)
                            //최대 치 계산
                            if (nowStroke > 0.5)
                                nowStroke = 0.5
                            nowStroke = round(nowStroke*100)/100 //실수 값을 소수점 한자리까지만 나오도록
                            binding.strokeTv.text = "Stroke : $nowStroke" //UI반영
                            result = nowStroke*1000 //결과 값 세팅
                            status = 0x04 //스테이더스 세팅
                        }
                        0x02.toByte() -> { //자동
                            //현재 값에 넘버픽커에 있는 값 계산
                            nowStroke += (binding.numberPicker.value * 0.01)
                            //최대 치 계산
                            if (nowStroke > 0.5)
                                nowStroke = 0.5
                            nowStroke = round(nowStroke*100)/100 //실수 값을 소수점 한자리까지만 나오도록
                            binding.strokeTv.text = "Stroke : $nowStroke" //UI반영
                            result = nowStroke*1000 //결과 값 세팅
                            status = 0x01 //스테이더스 세팅
                        }
                    }
                }
                binding.repeatBt.id -> { //반복 버튼
                    //현재 값에 넘버픽커에 있는 값 계산
                    nowRepeat += (binding.numberPicker.value*2)
                    //최대 치 계산
                    if (nowRepeat >100)
                        nowRepeat = 100.0
                    nowRepeat = round(nowRepeat*10)/10 //실수 값을 소수점 한자리까지만 나오도록
                    binding.repeatTv.text = "Repeat : $nowRepeat" //UI반영
                    result = nowRepeat //결과 값 세팅
                    status = 0x03 //스테이더스 세팅
                }
            }
            val arr = byteArrayOfInts(0x13,
                                            workoutStatus!!.toInt(),
                                            status!!,
                                            result.toInt().shr(8),
                                            result.toInt()) //커맨드 생성
            makeCommend(arr) //커맨드 전송
        }

        //빼기버튼 클릭 (플러스와 부호만 반대)
        binding.minusBt.setOnClickListener {
            var result = 0.0
            var status : Int? = null

            when(binding.radioGroup.checkedRadioButtonId){
                binding.loadBt.id -> {
                    nowLoad -= binding.numberPicker.value
                    if (nowLoad <1)
                        nowLoad = 1.0
                    nowLoad = round(nowLoad*10)/10
                    binding.loadTv.text = "Load : $nowLoad"
                    result = nowLoad*100
                    status = 0x01
                }
                binding.speedBt.id -> {
                    when(workoutStatus){
                        0x01.toByte() -> {
                            nowSpeed -= (binding.numberPicker.value * 0.1)
                            if (nowSpeed < 0.1)
                                nowSpeed = 0.1
                            nowSpeed = round(nowSpeed*10)/10
                            binding.speedTv.text = "Speed : $nowSpeed"
                            result = nowSpeed*1000
                        }
                        0x02.toByte() -> {
                            nowSpeed -= (binding.numberPicker.value * 0.1)
                            if (nowSpeed < 0.1)
                                nowSpeed = 0.1
                            nowSpeed = round(nowSpeed*10)/10
                            binding.speedTv.text = "Speed : $nowSpeed"
                            result = nowSpeed*1000
                        }
                    }
                    status = 0x02
                }
                binding.viscousBt.id -> {
                    nowViscous -= (binding.numberPicker.value*10)
                    if (nowViscous < 0)
                        nowViscous = 0.0
                    nowViscous = round(nowViscous*10)/10
                    binding.viscousTv.text = "Viscous : $nowViscous"
                    result = nowViscous
                    status = 0x03
                }
                binding.strokeBt.id -> {
                    when(workoutStatus){
                        0x01.toByte() -> {
                            nowStroke -= (binding.numberPicker.value * 0.01)
                            if (nowStroke < 0.2)
                                nowStroke = 0.2
                            nowStroke = round(nowStroke*100)/100
                            binding.strokeTv.text = "Stroke : $nowStroke"
                            result = nowStroke*1000
                            status = 0x04
                        }
                        0x02.toByte() -> {
                            nowStroke -= (binding.numberPicker.value * 0.01)
                            if (nowStroke < 0.2)
                                nowStroke = 0.2
                            nowStroke = round(nowStroke*100)/100
                            binding.strokeTv.text = "Stroke : $nowStroke"
                            result = nowStroke*1000
                            status = 0x01
                        }
                    }
                }
                binding.repeatBt.id -> {
                    nowRepeat -= (binding.numberPicker.value*2)
                    if (nowRepeat < 2)
                        nowRepeat = 2.0
                    nowRepeat = round(nowRepeat*10)/10
                    binding.repeatTv.text = "Repeat : $nowRepeat"
                    result = nowRepeat
                    status = 0x03
                }
            }
            val arr = byteArrayOfInts(0x13,
                                            workoutStatus!!.toInt(),
                                            status!!,
                                            result.toInt().shr(8),
                                            result.toInt())
            makeCommend(arr)
        }
    }

    //시리얼 포트 오픈
    private fun openSerialPort(name: String) {
        Log.d("gwan2103", "openSerialPort >>>> start")
        val serialPortFinder = SerialPortFinder()   //시리얼 포트 검색 객체
        val devices: Array<String> = serialPortFinder.allDevices    //모든 디바이스 검색
        val devicesPath: Array<String> = serialPortFinder.allDevicesPath    //모든 디바이스 경로 검색

        for (device in devices) {
            if (device.contains(name, true)) { //연결 할 시리얼 포트 이름과 중복되면 실행
                val index = devices.indexOf(device)
                Log.d("gwan2103", "devicesPath[$index] >>>> ${devicesPath[index]}")
                serialPort = SerialPort(File(devicesPath[index]), SERIAL_BAUDRATE, 0) //시리얼포트 오픈
            }
        }

        serialPort?.let { //연결된 시리얼 포트가 있으면
            Log.d("gwan2103", "openSerialPort >>>> let in")
            inputStream = it.inputStream    //데이터 인풋 스트림 정의
            outputStream = it.outputStream  //데이터 아웃풋 스트림 정의
        }
    }

    //데이터 수신
    private fun startRx() {
        if (inputStream == null) {  //연결된 데이터 인풋 스트림이 없으면 실행
            Log.e("SerialExam", "Can't open inputstream")
            return
        }

        CoroutineScope(Dispatchers.IO).launch{ //비동기 시작
            while (true) { //무한반복
                //Log.d("gwan2103","running???")
                var size: Int
                try {
                    if (inputStream != null) { //데이터 인풋 스트림이 정의되어 있으면 실행
                        val buffer = ByteArray(52) //데이터를 받아올 바이트어레이 선언 사이즈는 52
                        size = inputStream!!.read(buffer) //위에서 선언한 버퍼를 가지고 데이터를 받아옴
                        if (size > 0) { //받아온 데이터의 사이즈가 0보다 크면 실행
                            onReceiveData(buffer, size)
                        }
                    }else{ //데이터 인풋 스트림이 없을 때 실행
                        Log.d("gwan2103", "inputStream null")
                    }
                } catch (e: IOException) { //데이터 IO 익셉션처리
                    Log.d("gwan2103", "fail")
                    e.printStackTrace()
                }
            }
        }
    }

    //받아온 데이터를 가공하는 부분
    @SuppressLint("SetTextI18n")
    private fun onReceiveData(buffer: ByteArray, size: Int) {
        if (size < 1) return //받아온 데이터의 사이즈 체크
        //리시브 데이터 체크
        if ((buffer[0].toUByte().toInt() == 0xf1) && (buffer[1].toInt() <= 0x03)){
            // CRC16 계산 가비지 값이나 노이즈를 걸러내기 위함
            var crcTemp = 0xFFFF
            for (crcCnt in 3..buffer[2]+2){
                crcTemp = crcTemp.shr(8).xor(crc16Table[crcTemp.xor(buffer[crcCnt].toInt()).and(0xFF)])
            }
            // CRC16 데이터 체크
            // **** toUByte로 바이트가 음수로 계산되는것을 양수로 전환하여 처리한다.****
            if(crcTemp.shr(8).and(0xFF) == buffer[buffer[2]+3].toUByte().toInt() &&
                    crcTemp.and(0xFF) == buffer[buffer[2]+4].toUByte().toInt()){
                        //머신의 상태 값 초기화
                operStatus = buffer[3]
                modeOperStatus = buffer[4]
                operMode = buffer[5]
                workoutStatus = buffer[6]

                //왼쪽 손잡이의 포지션 계산
                val leftPosition = buffer[23].toInt().shl(8).or(buffer[24].toUByte().toInt())*0.001
                //왼쪽 손잡이의 포지션으로 운동 이동거리 계산
                if (modeOperStatus?.toInt() == 49) { //머신이 운동중 일 때만 계산하도록 필터링
                    nowPosition = round(leftPosition * 100) / 100 //Kotlin의 round함수를 사용하여 실수형 데이터를 반올림함
                    totalDistance += round(abs(nowPosition - prePosition) * 100) / 100 //Kotlin의 abs함수를 사용하여 절대값 계산
                    prePosition = nowPosition
                }
                //왼쪽의 운동 속도 계산
                val leftVelocity = buffer[25].toInt().shl(8).or(buffer[26].toUByte().toInt())*0.001
                //왼쪽 손잡이 부분의 노드셀 계산
                //**** 9.80665는 중력 가속도로 무게 값을 알기 위해 계산*****
                val leftLoadCell1 = buffer[27].toInt().shl(8).or(buffer[28].toUByte().toInt())*0.1/9.80665
                //왼쪽 발판 부분의 노드셀 계산
                val leftLoadCell2 = buffer[29].toInt().shl(8).or(buffer[30].toUByte().toInt())*0.1/9.80665

                //오른쪽 손잡이의 포지션 계산
                val rightPosition = buffer[31].toInt().shl(8).or(buffer[32].toUByte().toInt())*0.001
                //오른쪽의 운동 속도 계산
                val rightVelocity = buffer[33].toInt().shl(8).or(buffer[34].toUByte().toInt())*0.001
                //오른쪽 손잡이 부분의 노드셀 계산
                val rightLoadCell1 = buffer[35].toInt().shl(8).or(buffer[36].toUByte().toInt())*0.1/9.80665
                //오른쪽 발판 부분의 노드셀 계산
                val rightLoadCell2 = buffer[37].toInt().shl(8).or(buffer[38].toUByte().toInt())*0.1/9.80665

                CoroutineScope(Dispatchers.Main).launch { //비동기 시작
                    //UI 작업
                    binding.operStatusTv.text = "operStatus : $operStatus"
                    binding.modeOperStatusTv.text = "modeOperStatus : $modeOperStatus"
                    binding.operModeTv.text = "operMode : $operMode"
                    binding.workoutStatusTv.text = "workoutStatus : $workoutStatus"
                    binding.distanceTv.text = "distance : ${round(totalDistance * 100) / 100} 미터"

                    binding.leftPositionTv.text = "leftPosition : ${round(leftPosition * 100) / 100}"
                    binding.leftVelocityTv.text = "leftVelocity : ${round(leftVelocity * 100) / 100}"
                    binding.leftLoadCell1Tv.text = "leftLoadCell1 : ${round(leftLoadCell1 * 100) / 100}"
                    binding.leftLoadCell2Tv.text = "leftLoadCell2 : ${round(leftLoadCell2 * 100) / 100}"

                    binding.rightPositionTv.text = "rightPosition : ${round(rightPosition * 100) / 100}"
                    binding.rightVelocityTv.text = "rightVelocity : ${round(rightVelocity * 100) / 100}"
                    binding.rightLoadCell1Tv.text = "rightLoadCell1 : ${round(rightLoadCell1 * 100) / 100}"
                    binding.rightLoadCell2Tv.text = "rightLoadCell2 : ${round(rightLoadCell2 * 100) / 100}"
                }
            }
            val strBuilder = StringBuilder()
            for (i in 0 until size) {
                strBuilder.append(String.format("%02x ", buffer[i]))
            }
            Log.d("SerialExam", "rx: $strBuilder")
        }

        /*operStatus = buffer[3]
        modeOperStatus = buffer[4]
        operMode = buffer[5]
        workoutStatus = buffer[6]
        val leftPosition = buffer[23].toInt().shl(8).or(buffer[24].toUByte().toInt())*0.001

        if (modeOperStatus?.toInt() == 49) {
            nowPosition = round(leftPosition * 100) / 100
            totalDistance += round(abs(nowPosition - prePosition) * 100) / 100
            prePosition = nowPosition
        }

        val leftVelocity = buffer[25].toInt().shl(8).or(buffer[26].toInt())*0.001
        val leftLoadCell1 = buffer[27].toInt().shl(8).or(buffer[28].toInt())*0.1
        val leftLoadCell2 = buffer[29].toInt().shl(8).or(buffer[30].toInt())*0.1

        val rightPosition = buffer[31].toInt().shl(8).or(buffer[32].toUByte().toInt())*0.001
        val rightVelocity = buffer[33].toInt().shl(8).or(buffer[34].toInt())*0.001
        val rightLoadCell1 = buffer[35].toInt().shl(8).or(buffer[36].toInt())*0.1
        val rightLoadCell2 = buffer[37].toInt().shl(8).or(buffer[38].toInt())*0.1

        CoroutineScope(Dispatchers.Main).launch {
            binding.operStatusTv.text = "operStatus : $operStatus"
            binding.modeOperStatusTv.text = "modeOperStatus : $modeOperStatus"
            binding.operModeTv.text = "operMode : $operMode"
            binding.workoutStatusTv.text = "workoutStatus : $workoutStatus"
            binding.distanceTv.text = "distance : ${round(totalDistance * 100) / 100} 미터"

            binding.leftPositionTv.text = "leftPosition : ${round(leftPosition * 100) / 100}"
            binding.leftVelocityTv.text = "leftVelocity : ${round(leftVelocity * 100) / 100}"
            binding.leftLoadCell1Tv.text = "leftLoadCell1 : ${round(leftLoadCell1 * 100) / 100}"
            binding.leftLoadCell2Tv.text = "leftLoadCell2 : ${round(leftLoadCell2 * 100) / 100}"

            binding.rightPositionTv.text = "rightPosition : ${round(rightPosition * 100) / 100}"
            binding.rightVelocityTv.text = "rightVelocity : ${round(rightVelocity * 100) / 100}"
            binding.rightLoadCell1Tv.text = "rightLoadCell1 : ${round(rightLoadCell1 * 100) / 100}"
            binding.rightLoadCell2Tv.text = "rightLoadCell2 : ${round(rightLoadCell2 * 100) / 100}"
        }*/
    }

    //데이터 전송부분
    private fun sendData(data: ByteArray) {
        try {
            outputStream?.write(data)
        } catch (e: IOException) {
            Log.d("gwan2103", "sendData >>>> fail")
            e.printStackTrace()
        }
    }

    // 정수 바이트 어레이로 변환해서 반환
    private fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }

    //전송할 바이트 데이터 셋을 만드는 부분
    private fun makeCommend(dataArray: ByteArray){
        //데이터를 담을 바이트 어레이 생성 +6하는 이유는 기초 데이터 셋의 개수
        val buffer = ByteArray(dataArray.size + 6)
        var crcTemp = 0xFFFF
        //Log.d("gwan2103", "crcTemp >>>>> $crcTemp ")
        buffer[0] = 0xF1.toByte()   //첫번째 데이터 전송 규약
        buffer[1] = 0x01.toByte()   //두번째 데이터 전송 규약
        buffer[2] = dataArray.size.toByte() //전송한 데이터의 사이즈
        for (index in dataArray.indices){   //전송할 데이터 사이즈만큼 추가
            buffer[index + 3] = dataArray[index]
            crcTemp = crcTemp.shr(8).xor(crc16Table[crcTemp.xor(dataArray[index].toInt()).and(0xFF)])
            //Log.d("gwan2103", "buffer crcTemp >>>>> ${crcTemp.toUInt().toString(16)} ")
        }
        buffer[3 + dataArray.size] = crcTemp.shr(8).and(0xFF).toByte() //첫번째 CRC16 저장
        buffer[4 + dataArray.size] = crcTemp.and(0xFF).toByte() //두번째 CRC16 저장
        buffer[5 + dataArray.size] = 0xFE.toByte() //마무리 데이터 전송 규약
        for (i in buffer.indices){
            Log.d("gwan2103", "buffer data $i >>>>> ${buffer[i].toUInt().toString(16)} ")
        }
        //데이터 전송
        sendData(buffer)
    }

    // 1초마다 자동으로 데이터 전송 (테스트 용도)
    private fun autoSend(){
        CoroutineScope(Dispatchers.IO).launch{
            while (true) {
                Log.d("Gwan2103","aaaa")
                /*val arr = byteArrayOfInts(0x80)
                makeCommend(arr)*/
                val buffer = ByteArray(7)
                buffer[0] = 0xff.toByte()
                buffer[1] = 0xff.toByte()
                buffer[2] = 0xff.toByte()
                buffer[3] = 0xff.toByte()
                buffer[4] = 0xff.toByte()
                buffer[5] = 0xff.toByte()
                buffer[6] = 0xff.toByte()
                sendData(buffer)
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("gwan2103","onDestroy")
        serialPort?.close()
    }

    companion object{
            const val SERIAL_PORT_NAME = "ttyS1" //연결할 시리얼 포트 이름
            const val SERIAL_BAUDRATE = 115200 //연결할 시리얼 포트 보드레이트

            //CRC16 테이블 정의
            val crc16Table = arrayOf(0X0000, 0XC0C1, 0XC181, 0X0140, 0XC301, 0X03C0, 0X0280, 0XC241,
                0XC601, 0X06C0, 0X0780, 0XC741, 0X0500, 0XC5C1, 0XC481, 0X0440,
                0XCC01, 0X0CC0, 0X0D80, 0XCD41, 0X0F00, 0XCFC1, 0XCE81, 0X0E40,
                0X0A00, 0XCAC1, 0XCB81, 0X0B40, 0XC901, 0X09C0, 0X0880, 0XC841,
                0XD801, 0X18C0, 0X1980, 0XD941, 0X1B00, 0XDBC1, 0XDA81, 0X1A40,
                0X1E00, 0XDEC1, 0XDF81, 0X1F40, 0XDD01, 0X1DC0, 0X1C80, 0XDC41,
                0X1400, 0XD4C1, 0XD581, 0X1540, 0XD701, 0X17C0, 0X1680, 0XD641,
                0XD201, 0X12C0, 0X1380, 0XD341, 0X1100, 0XD1C1, 0XD081, 0X1040,
                0XF001, 0X30C0, 0X3180, 0XF141, 0X3300, 0XF3C1, 0XF281, 0X3240,
                0X3600, 0XF6C1, 0XF781, 0X3740, 0XF501, 0X35C0, 0X3480, 0XF441,
                0X3C00, 0XFCC1, 0XFD81, 0X3D40, 0XFF01, 0X3FC0, 0X3E80, 0XFE41,
                0XFA01, 0X3AC0, 0X3B80, 0XFB41, 0X3900, 0XF9C1, 0XF881, 0X3840,
                0X2800, 0XE8C1, 0XE981, 0X2940, 0XEB01, 0X2BC0, 0X2A80, 0XEA41,
                0XEE01, 0X2EC0, 0X2F80, 0XEF41, 0X2D00, 0XEDC1, 0XEC81, 0X2C40,
                0XE401, 0X24C0, 0X2580, 0XE541, 0X2700, 0XE7C1, 0XE681, 0X2640,
                0X2200, 0XE2C1, 0XE381, 0X2340, 0XE101, 0X21C0, 0X2080, 0XE041,
                0XA001, 0X60C0, 0X6180, 0XA141, 0X6300, 0XA3C1, 0XA281, 0X6240,
                0X6600, 0XA6C1, 0XA781, 0X6740, 0XA501, 0X65C0, 0X6480, 0XA441,
                0X6C00, 0XACC1, 0XAD81, 0X6D40, 0XAF01, 0X6FC0, 0X6E80, 0XAE41,
                0XAA01, 0X6AC0, 0X6B80, 0XAB41, 0X6900, 0XA9C1, 0XA881, 0X6840,
                0X7800, 0XB8C1, 0XB981, 0X7940, 0XBB01, 0X7BC0, 0X7A80, 0XBA41,
                0XBE01, 0X7EC0, 0X7F80, 0XBF41, 0X7D00, 0XBDC1, 0XBC81, 0X7C40,
                0XB401, 0X74C0, 0X7580, 0XB541, 0X7700, 0XB7C1, 0XB681, 0X7640,
                0X7200, 0XB2C1, 0XB381, 0X7340, 0XB101, 0X71C0, 0X7080, 0XB041,
                0X5000, 0X90C1, 0X9181, 0X5140, 0X9301, 0X53C0, 0X5280, 0X9241,
                0X9601, 0X56C0, 0X5780, 0X9741, 0X5500, 0X95C1, 0X9481, 0X5440,
                0X9C01, 0X5CC0, 0X5D80, 0X9D41, 0X5F00, 0X9FC1, 0X9E81, 0X5E40,
                0X5A00, 0X9AC1, 0X9B81, 0X5B40, 0X9901, 0X59C0, 0X5880, 0X9841,
                0X8801, 0X48C0, 0X4980, 0X8941, 0X4B00, 0X8BC1, 0X8A81, 0X4A40,
                0X4E00, 0X8EC1, 0X8F81, 0X4F40, 0X8D01, 0X4DC0, 0X4C80, 0X8C41,
                0X4400, 0X84C1, 0X8581, 0X4540, 0X8701, 0X47C0, 0X4680, 0X8641,
                0X8201, 0X42C0, 0X4380, 0X8341, 0X4100, 0X81C1, 0X8081, 0X4040)
    }
}