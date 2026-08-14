import Foundation

/// EmergencyPacket v1 — little endian. Debe coincidir con Kotlin `EmergencyPacketCodec`.
public struct RdaPacket {
    public static let headerSize = 48
    public static let authSize = 16
    public static let serviceUUID = "a1e00001-5244-4159-0001-726564617975"

    public var version: UInt8
    public var type: UInt8
    public var flags: UInt8
    public var ttl: UInt8
    public var hopCount: UInt8
    public var eventType: UInt8
    public var status: UInt8
    public var battery: UInt8
    public var messageId: Data
    public var originDeviceId: Data
    public var timestamp: UInt32
    public var latitude: Int32
    public var longitude: Int32
    public var accuracyMeters: UInt16
    public var payload: Data
    public var auth: Data

    public func encode() -> Data {
        var data = Data()
        func u8(_ v: UInt8) { data.append(v) }
        func u16(_ v: UInt16) {
            var le = v.littleEndian
            data.append(Data(bytes: &le, count: 2))
        }
        func u32(_ v: UInt32) {
            var le = v.littleEndian
            data.append(Data(bytes: &le, count: 4))
        }
        func i32(_ v: Int32) {
            var le = v.littleEndian
            data.append(Data(bytes: &le, count: 4))
        }
        u8(version); u8(type); u8(flags); u8(ttl); u8(hopCount)
        u8(eventType); u8(status); u8(battery)
        data.append(messageId)
        data.append(originDeviceId)
        u32(timestamp)
        i32(latitude)
        i32(longitude)
        u16(accuracyMeters)
        u16(UInt16(payload.count))
        data.append(payload)
        data.append(auth)
        return data
    }

    public static func decode(_ data: Data) throws -> RdaPacket {
        guard data.count >= headerSize + authSize else { throw RdaError.tooShort }
        var o = 0
        func u8() -> UInt8 { defer { o += 1 }; return data[o] }
        func slice(_ n: Int) -> Data { defer { o += n }; return data.subdata(in: o..<(o + n)) }
        func u16() -> UInt16 {
            let v = UInt16(data[o]) | UInt16(data[o + 1]) << 8
            o += 2
            return v
        }
        func i32() -> Int32 {
            let v = Int32(truncatingIfNeeded:
                UInt32(data[o]) |
                UInt32(data[o + 1]) << 8 |
                UInt32(data[o + 2]) << 16 |
                UInt32(data[o + 3]) << 24)
            o += 4
            return v
        }
        func u32() -> UInt32 {
            let v = UInt32(data[o]) |
                UInt32(data[o + 1]) << 8 |
                UInt32(data[o + 2]) << 16 |
                UInt32(data[o + 3]) << 24
            o += 4
            return v
        }
        let version = u8()
        guard version == 1 else { throw RdaError.version }
        let type = u8()
        let flags = u8()
        let ttl = u8()
        let hop = u8()
        let event = u8()
        let status = u8()
        let battery = u8()
        let mid = slice(16)
        let oid = slice(8)
        let ts = u32()
        let lat = i32()
        let lon = i32()
        let acc = u16()
        let plen = Int(u16())
        guard data.count >= headerSize + plen + authSize else { throw RdaError.tooShort }
        let payload = slice(plen)
        let auth = slice(16)
        return RdaPacket(
            version: version, type: type, flags: flags, ttl: ttl, hopCount: hop,
            eventType: event, status: status, battery: battery,
            messageId: mid, originDeviceId: oid, timestamp: ts,
            latitude: lat, longitude: lon, accuracyMeters: acc,
            payload: payload, auth: auth
        )
    }
}

public enum RdaError: Error {
    case tooShort
    case version
}
