#!/usr/bin/env python3
"""Real authenticated local API checks; never prints passwords or tokens."""
import json, urllib.request, urllib.error, struct, zlib, uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
fixture=json.loads((ROOT/'.local/acceptance/accounts.json').read_text())
base=fixture['api']; a=fixture['accounts'][0]
def request(path,method='GET',body=None,token=None,content_type='application/json'):
    data=body if isinstance(body,bytes) else json.dumps(body).encode() if body is not None else None
    headers={'Content-Type':content_type}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path,data,headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=100) as response: return json.load(response)
    except urllib.error.HTTPError as error:
        return json.load(error)
def ok(value):
    assert value.get('code')==200, 'API request failed (details suppressed)'
    return value.get('data')
s=ok(request('/api/user/login','POST',dict(username=a['username'],password=a['password'])))
token=s['token']; role=a['roles'][0]; checks=[]; report={'checks':checks}
try:
    p=ok(request('/api/user/robot-profile',token=token));assert isinstance(p['roleId'],int) and isinstance(p['roles'],list);checks.append('real login and robot-profile DTO')
    weather=ok(request('/api/robot/weather?latitude=10.7769&longitude=106.7009',token=token))
    assert weather['source']=='Open-Meteo' and isinstance(weather['temperatureC'],(float,int));checks.append('real weather over backend HTTPS')
    memories=ok(request(f'/api/robot/memories?roleId={role}',token=token))['list']
    m=next(m for m in memories if m['summary']=='Disposable acceptance memory')
    path=f'/api/robot/memories/{role}/{m["id"]}'
    assert ok(request(path,'PUT',{'text':'Disposable acceptance edit'},token))==1
    assert ok(request(path,'DELETE',{},token))==1
    assert all(x['id']!=m['id'] for x in ok(request(f'/api/robot/memories?roleId={role}',token=token))['list'])
    foreign=fixture['accounts'][1]['roles'][0]
    for method,route,body in [('GET',f'/api/robot/memories?roleId={foreign}',None),('PUT',f'/api/robot/memories/{foreign}/{m["id"]}',{'text':'rejected'}),('DELETE',f'/api/robot/memories/{foreign}/{m["id"]}',{})]:
        assert request(route,method,body,token).get('code')!=200
    checks.append('memory read, edit, delete and foreign-user rejection')
    def chunk(name,data): return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data)&0xffffffff)
    image=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+b'\xff\0\0'*64)*64))+chunk(b'IEND',b'')
    boundary=uuid.uuid4().hex
    def multipart(consent):
        fields={'consent':consent,'question':'Hình này chủ yếu có màu gì? Trả lời ngắn bằng tiếng Việt.'}
        parts=[f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode() for key,value in fields.items()]
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="image"; filename="synthetic.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+image+b'\r\n')
        return b''.join(parts)+f'--{boundary}--\r\n'.encode()
    mime='multipart/form-data; boundary='+boundary
    assert request('/api/robot/vision','POST',multipart('false'),token,mime).get('code')!=200
    checks.append('cloud vision rejected without explicit consent')
    answer=ok(request('/api/robot/vision','POST',multipart('true'),token,mime))['answer']
    assert 'đỏ' in answer.lower(), 'Vision did not recognize the synthetic fixture'
    report['visionAnswer']=answer;checks.append('synthetic red image through real VisionService and model')
    ok(request('/api/user/logout','POST',{},token));assert request('/api/user/check-token',token=token).get('code')!=200
    checks.append('logout revokes the real token');report['result']='passed'
except BaseException as error:
    report['result']='failed';report['error']=str(error);raise
finally:
    (ROOT/'.local/acceptance/backend-features-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps(report,ensure_ascii=False))
