#!/bin/sh
set -eu

RULE_DIR=/etc/udev/rules.d
RULE_NAME=71-phonepad-uinput.rules
SOURCE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/$RULE_NAME

if [ "$(id -u)" -ne 0 ]; then
    echo "Run with administrator rights: sudo $0" >&2
    exit 1
fi

install -m 0644 "$SOURCE" "$RULE_DIR/$RULE_NAME"
udevadm control --reload-rules
udevadm trigger --action=change --subsystem-match=input
udevadm trigger --action=change --subsystem-match=hidraw
echo "Installed PhonePad input access rule. Restart PhonePad Companion and reconnect the phone."
