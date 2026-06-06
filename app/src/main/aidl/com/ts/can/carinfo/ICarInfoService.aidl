package com.ts.can.carinfo;

interface ICarInfoService{
    int requestCarAirInfo(String str);
    String requestCarAirLtTemp();
    String requestCarAirRtTemp();
    int[] requestCarDoorInfo();
    boolean requestCarIllInfo();
    int[] requestCarBaseInfo();

    int[] requestT3FlDevInfo();
    int requestT3FlSta();
    int[] requestT3FlCanData7f1();
    int[] requestT3FlCanData7f2();
    int[] requestT3FlCanData7f3();
    int[] requestT3FlCanData7f4();
    int[] requestT3FlCanData7e0();

    int[] requestT3FlTexlData();
    int T3FlTexlCmd(int type, in int[] cmd);
    int[] requestT3FlTexlDisCur();
    int[] requestT3FlTexlDisOver();
    int[] requestT3FlTexlPjxx();

    int SendCmd(int type, in int[] cmd);

    int UartOpen(int speed);
    int UartClose();
    int UartRead(out byte[] pbuf, int len);
    int UartSend(in byte[] pbuf, int len);
    int UartCanSend(in byte[] pbuf, int len);

    int[] requestCanRecevieData(int para);
    int requestInfo(String str, int para);
    int requestCarBaseInfo2(String str, int para);
}
