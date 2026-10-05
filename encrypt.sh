#!/data/data/com.termux/files/usr/bin/bash
# Encrypt bot token + chat id for Config.java
# Usage: ./encrypt.sh "<bot_token>" "<chat_id>"

TOKEN="$1"
CHAT="$2"

if [ -z "$TOKEN" ] || [ -z "$CHAT" ]; then
    echo "Usage: ./encrypt.sh <bot_token> <chat_id>"
    exit 1
fi

KEY="5A3C91E72D8841B617C36F9AD4227B05"

enc() {
    local input="$1"
    local out=""
    local klen=${#KEY}
    for (( i=0; i<${#input}; i++ )); do
        local c=$(printf '%d' "'${input:$i:1}")
        local k=$(( 16#${KEY:$(( (i*2) % klen )):2} ))
        local x=$(( c ^ k ))
        out+=$(printf '\\x%02x' $x)
    done
    printf "$out" | base64 -w0
}

echo "=== Paste these into Config.java ==="
echo ""
echo "T_B64:"
enc "$TOKEN"
echo ""
echo ""
echo "C_B64:"
enc "$CHAT"
echo ""
