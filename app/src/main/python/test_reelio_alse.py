import sys
import json
import reelio_alse

def main():
    try:
        with open('insta_data.csv', 'r', encoding='utf-8') as f:
            csv_data = f.read()
    except UnicodeDecodeError:
        with open('insta_data.csv', 'r', encoding='utf-16') as f:
            csv_data = f.read()
            
    result = reelio_alse.run_dashboard_payload(csv_data)
    try:
        parsed = json.loads(result)
        with open('out_reelio.json', 'w', encoding='utf-8') as wf:
            json.dump(parsed, wf, indent=2)
        print("Successfully wrote out_reelio.json")
    except Exception as e:
        print("Exception:", e)

if __name__ == '__main__':
    main()
