import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from connection_qr import PREFIX, encode_payload


class ConnectionQrTests(unittest.TestCase):
    def test_payload_contains_connection_data_without_manual_entry(self):
        encoded = encode_payload('192.168.1.8', 39570, 'ab' * 91, 'device-id', 'Gaming PC', 'cd' * 16, '234567AB')
        self.assertTrue(encoded.startswith(PREFIX))
        self.assertEqual(json.loads(encoded[len(PREFIX):]), {
            'v': 1, 'host': '192.168.1.8', 'port': 39570, 'public': 'ab' * 91,
            'id': 'device-id', 'name': 'Gaming PC', 'token': 'cd' * 16, 'code': '234567AB',
        })

    def test_invalid_address_port_or_key_is_rejected(self):
        for args in [('', 39570, 'ab' * 91, 'cd' * 16, '234567AB'), ('192.168.1.8', 0, 'ab' * 91, 'cd' * 16, '234567AB'),
                     ('192.168.1.8', 39570, 'short', 'cd' * 16, '234567AB'), ('192.168.1.8', 39570, 'ab' * 91, 'cd' * 16, 'bad-code')]:
            with self.subTest(args=args), self.assertRaises(ValueError):
                encode_payload(args[0], args[1], args[2], 'id', 'PC', args[3], args[4])


if __name__ == '__main__':
    unittest.main()
