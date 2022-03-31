#create function
import pandas as pd
from math import sin, cos, sqrt, atan2, radians
import os
from os.path import dirname, join
from datetime import datetime


def gps_dis(lat1,lon1,lat2,lon2):
    R = 6373.0
    lat1 = radians(lat1)
    lon1 = radians(lon1)
    lat2 = radians(lat2)
    lon2 = radians(lon2)
    #print(lat1,lon1,lat2,lon2)
    dlon = lon2 - lon1
    dlat = lat2 - lat1
    a = sin(dlat / 2)**2 + cos(lat1) * cos(lat2) * sin(dlon / 2)**2
    c = 2 * atan2(sqrt(a), sqrt(1 - a))
    distance = R * c*1000
    return int(round(distance))

def main(csvData, gps_lat,gps_long):
    print("PYTHON RUNNING..")
    print('Current directory: ' + os.getcwd())
    gps = (22.374847913295106, 114.17412409790698)
    #gps_lat = gps[0]
    #gps_long = gps[1]
    #22.321020,114.179839
    fileLink = 'parkingspaces.csv'
    fileLink = join(dirname(__file__), fileLink)
    url = r'https://resource.data.one.gov.hk/td/psiparkingspaces/occupancystatus/occupancystatus.csv'
    #url = join(dirname(__file__), 'occupancystatus.csv')
    #df = pd.read_csv(url)
    df = csvData
    #print(df)

    df = df[df['ParkingMeterStatus']=='N']
    #df = df[df['OccupancyStatus']=='V']
    df = df[['ParkingSpaceId','OccupancyStatus','OccupancyDateChanged']]
    #print(df)

    df2 = pd.read_csv(fileLink, skiprows=2)
    df2 = df2[['ParkingSpaceId','Latitude','Longitude','Street','Street_tc','VehicleType']]
    df2 = df2[df2['VehicleType']=='A']
    #print(df2)

    df = pd.merge(df,df2)
    #print(df)

    df['mht_dist']=(abs(df['Latitude']-gps_lat)+abs(df['Longitude']-gps_long))
    df = df.sort_values(by=['mht_dist'])
    df = df.head(50)
    #df['gps_lat']=gps_lat
    #df['gps_long']=gps_long
    #print(df)

    df['real_dist']=df.apply(lambda row: gps_dis(row['Latitude'],row['Longitude'],gps_lat,gps_long), axis=1)
    #print(df)

    df = df[['ParkingSpaceId','real_dist','Latitude','Longitude','Street','Street_tc','OccupancyStatus','OccupancyDateChanged']]
    df = df.sort_values(by=['real_dist'])
    #print(df)

    #dff = []
    #for i in range(10):
    #    dff.append(df.iloc[i].to_numpy().flatten())

    #print("ddddd")
    #print(dff)

    dff = df.to_numpy().flatten()
    #print(df)

    #temp_loc = (22.262045, 114.179574)
    #location = (gps_lat,gps_long)

    #distance = gps_dis(temp_loc,location)
    #return "Distance: " + str(distance) + 'm'
    return dff


def getVacancyCSV():
    url = r'https://resource.data.one.gov.hk/td/psiparkingspaces/occupancystatus/occupancystatus.csv'
    for __ in range(10):
        try:
            dff = pd.read_csv(url)
            break
        except:
            print("SSLError: [SSL: DECRYPTION_FAILED_OR_BAD_RECORD_MAC] decryption failed or bad record mac")
    #dff = df.to_numpy().flatten()
    return dff

#print(main(getVacancyCSV(),22.369798, 114.180212))
