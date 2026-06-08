package main

import (
	"bufio"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

var (
	listen = flag.String("listen", ":6080", "HTTP/WebSocket listen address")
	staticDir = flag.String("static", "/data/local/tmp/novnc", "noVNC static asset directory")
	vncAddr = flag.String("vnc", "127.0.0.1:5900", "raw VNC server address")
)

func main() {
	flag.Parse()
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)

	mux := http.NewServeMux()
	mux.HandleFunc("/websockify", handleWS)
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/" {
			http.Redirect(w, r, "/vnc.html", http.StatusFound)
			return
		}
		p := filepath.Clean(r.URL.Path)
		if strings.Contains(p, "..") {
			http.NotFound(w, r)
			return
		}
		http.FileServer(http.Dir(*staticDir)).ServeHTTP(w, r)
	})

	log.Printf("android-novnc-proxy listening on %s, static=%s, vnc=%s", *listen, *staticDir, *vncAddr)
	server := &http.Server{Addr: *listen, Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	log.Fatal(server.ListenAndServe())
}

func handleWS(w http.ResponseWriter, r *http.Request) {
	if !strings.EqualFold(r.Header.Get("Upgrade"), "websocket") {
		http.Error(w, "websocket upgrade required", http.StatusUpgradeRequired)
		return
	}
	key := r.Header.Get("Sec-WebSocket-Key")
	if key == "" {
		http.Error(w, "missing Sec-WebSocket-Key", http.StatusBadRequest)
		return
	}
	hj, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "hijacking unsupported", http.StatusInternalServerError)
		return
	}

	vnc, err := net.DialTimeout("tcp", *vncAddr, 5*time.Second)
	if err != nil {
		http.Error(w, "vnc dial failed: "+err.Error(), http.StatusBadGateway)
		return
	}

	client, rw, err := hj.Hijack()
	if err != nil {
		vnc.Close()
		return
	}

	accept := wsAccept(key)
	protoHeader := ""
	if strings.Contains(r.Header.Get("Sec-WebSocket-Protocol"), "binary") {
		protoHeader = "Sec-WebSocket-Protocol: binary\r\n"
	}
	_, _ = fmt.Fprintf(client, "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\n%s\r\n", accept, protoHeader)
	log.Printf("websocket connected from %s ua=%q proto=%q path=%q", r.RemoteAddr, r.UserAgent(), r.Header.Get("Sec-WebSocket-Protocol"), r.URL.String())

	done := make(chan struct{}, 2)
	go func() {
		defer func(){ done <- struct{}{} }()
		err := wsToTCP(rw.Reader, vnc); log.Printf("client->vnc ended from %s: %v", r.RemoteAddr, err)
	}()
	go func() {
		defer func(){ done <- struct{}{} }()
		err := tcpToWS(vnc, client); log.Printf("vnc->client ended from %s: %v", r.RemoteAddr, err)
	}()
	<-done
	_ = client.Close()
	_ = vnc.Close()
	log.Printf("websocket disconnected from %s", r.RemoteAddr)
}

func wsAccept(key string) string {
	h := sha1.New()
	_, _ = h.Write([]byte(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))
	return base64.StdEncoding.EncodeToString(h.Sum(nil))
}

func wsToTCP(br *bufio.Reader, dst net.Conn) error {
	for {
		op, payload, err := readFrame(br)
		if err != nil { return err }
		log.Printf("ws frame opcode=%d len=%d", op, len(payload))
		switch op {
		case 0x1, 0x2, 0x0:
			if len(payload) > 0 {
				if _, err := dst.Write(payload); err != nil { return err }
			}
		case 0x8:
			return io.EOF
		case 0x9:
			// Ping: ignore; browser will tolerate missing pong for this simple proxy in practice.
		case 0xA:
			// Pong.
		default:
			// Ignore unsupported control frames.
		}
	}
}

func tcpToWS(src net.Conn, dst net.Conn) error {
	buf := make([]byte, 32768)
	for {
		n, err := src.Read(buf)
		if n > 0 {
			log.Printf("tcp chunk len=%d first=%q", n, string(buf[:min(n, 32)]))
			if err2 := writeFrame(dst, 0x2, buf[:n]); err2 != nil { return err2 }
		}
		if err != nil { return err }
	}
}

func readFrame(r *bufio.Reader) (byte, []byte, error) {
	b0, err := r.ReadByte()
	if err != nil { return 0, nil, err }
	b1, err := r.ReadByte()
	if err != nil { return 0, nil, err }
	opcode := b0 & 0x0f
	masked := b1&0x80 != 0
	ln := uint64(b1 & 0x7f)
	if ln == 126 {
		var tmp [2]byte
		if _, err := io.ReadFull(r, tmp[:]); err != nil { return 0, nil, err }
		ln = uint64(binary.BigEndian.Uint16(tmp[:]))
	} else if ln == 127 {
		var tmp [8]byte
		if _, err := io.ReadFull(r, tmp[:]); err != nil { return 0, nil, err }
		ln = binary.BigEndian.Uint64(tmp[:])
	}
	if ln > 16*1024*1024 { return 0, nil, fmt.Errorf("frame too large: %d", ln) }
	var mask [4]byte
	if masked {
		if _, err := io.ReadFull(r, mask[:]); err != nil { return 0, nil, err }
	}
	payload := make([]byte, ln)
	if _, err := io.ReadFull(r, payload); err != nil { return 0, nil, err }
	if masked {
		for i := range payload { payload[i] ^= mask[i%4] }
	}
	return opcode, payload, nil
}

func writeFrame(w io.Writer, opcode byte, payload []byte) error {
	header := []byte{0x80 | opcode}
	ln := len(payload)
	if ln < 126 {
		header = append(header, byte(ln))
	} else if ln <= 65535 {
		header = append(header, 126, byte(ln>>8), byte(ln))
	} else {
		header = append(header, 127)
		var tmp [8]byte
		binary.BigEndian.PutUint64(tmp[:], uint64(ln))
		header = append(header, tmp[:]...)
	}
	if _, err := w.Write(header); err != nil { return err }
	_, err := w.Write(payload)
	return err
}

func min(a,b int) int { if a < b { return a }; return b }

func init() { _ = os.Stdout }
