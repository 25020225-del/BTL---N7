import os

script_dir = os.path.dirname(os.path.abspath(__file__))
project_dir = os.path.dirname(script_dir)
output_dir = os.path.join(script_dir, 'Result')
output_filename = 'fpp.txt'

allowed_extensions = ('.java', '.fxml', '.css')

def merge_files():
    os.makedirs(output_dir, exist_ok=True)
    
    output_filepath = os.path.join(output_dir, output_filename)
    
    with open(output_filepath, 'w', encoding='utf-8') as outfile:
        for root, dirs, files in os.walk(project_dir):
            if script_dir in os.path.abspath(root):
                continue
                
            for file in files:
                if file.endswith(allowed_extensions):
                    filepath = os.path.join(root, file)
                    
                    outfile.write(f"\n{'='*70}\n")
                    outfile.write(f"/// FILE: {filepath} ///\n")
                    outfile.write(f"{'='*70}\n\n")
                    
                    try:
                        with open(filepath, 'r', encoding='utf-8') as infile:
                            outfile.write(infile.read())
                            outfile.write("\n")
                    except Exception as e:
                        outfile.write(f"// File reading error: {e} //\n")
                        
    return output_filepath

if __name__ == '__main__':
    print("Reading...")
    result_path = merge_files()
    print("Completed")
