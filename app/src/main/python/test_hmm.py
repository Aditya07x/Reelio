import sys
import json
import hmm

def main():
    try:
        with open('insta_data.csv', 'r', encoding='utf-8') as f:
            csv_data = f.read()
    except UnicodeDecodeError:
        with open('insta_data.csv', 'r', encoding='utf-16') as f:
            csv_data = f.read()
            
    result = hmm.run_hmm_from_string(csv_data)
    try:
        parsed = json.loads(result)
        with open('out.json', 'w', encoding='utf-8') as wf:
            json.dump(parsed, wf, indent=2)
        print("Successfully wrote out.json")
    except Exception as e:
        print("Exception:", e)

if __name__ == '__main__':
    main()
