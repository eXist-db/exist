
using System.Windows.Forms;
using System.Drawing;

public class QueryForm : Form {
    
    Label queryLabel;
    Label statusBar;
    TextBox query;
    TextBox view;
    Button searchButton;
    QueryService service = new QueryService();
    
    public QueryForm() {
        this.Text = "eXist SOAP example";
        this.Size = new Size(500, 400);
        
        statusBar = new Label();
        statusBar.Dock = DockStyle.Fill;
        
        Panel statusPanel = new Panel();
        statusPanel.Dock = DockStyle.Bottom;
        statusPanel.Height = statusBar.Height;
        statusPanel.BorderStyle = BorderStyle.Fixed3D;
        statusPanel.Controls.Add(statusBar);
        
        searchButton = new Button();
        searchButton.Text = "Send";
        searchButton.Name = "searchButton";
        searchButton.Dock = DockStyle.Right;
        searchButton.Click += new System.EventHandler(searchButton_Click);
        
        queryLabel = new Label();
        queryLabel.Text = "Search for:";
        queryLabel.Dock = DockStyle.Left;
        queryLabel.Width = queryLabel.PreferredWidth;
        queryLabel.Height = searchButton.Height;
        queryLabel.TextAlign = ContentAlignment.MiddleLeft;
        
        query = new TextBox();
        query.Dock = DockStyle.Right;
        query.AutoSize = false;
        query.Width = this.Width - queryLabel.Width - 10 - searchButton.Width;
        query.Height = searchButton.Height;
        query.Multiline = false;
        
        Panel panel1 = new Panel();
        panel1.Dock = DockStyle.Left;
        panel1.Width = query.Width + queryLabel.Width + 10;
        panel1.Controls.AddRange(new Control[]{queryLabel, query});
        
        Panel searchPanel = new Panel();
        searchPanel.Size = new Size(this.Width - 10, 35);
        searchPanel.Dock = DockStyle.Top;
        searchPanel.BorderStyle = System.Windows.Forms.BorderStyle.FixedSingle;
        searchPanel.Controls.AddRange(new Control[]{panel1, searchButton});
        
        view = new TextBox();
        view.Multiline = true;
        view.WordWrap = true;
        view.Height = this.Height - statusPanel.Height - searchPanel.Height - 25;
        view.Dock = DockStyle.Bottom;
        view.ScrollBars = ScrollBars.Vertical;
        
        Panel topPanel = new Panel();
        topPanel.Dock = DockStyle.Fill;
        topPanel.Controls.AddRange(new Control[] {searchPanel, view});
        topPanel.Height = this.Height - statusPanel.Height;
        
        this.Controls.AddRange(new Control[] { topPanel, statusPanel });
    }
    
    private void searchButton_Click(object sender, System.EventArgs e) {
        string xpath = query.Text;
        statusBar.Text = string.Format("Sending query: {0}", xpath);
        Cursor.Current = Cursors.WaitCursor;
        QueryResponse resp = service.query(xpath);
        statusBar.Text = string.Format("Found {0} hits.", resp.hits);
        
        for(int i = 1; i <= 20 && i <= resp.hits; i++) { 
            byte[] record = service.retrieve(resp.resultSetId, i, "UTF-8",
                    true);
            string str = System.Text.Encoding.UTF8.GetString(record);
            
            view.AppendText(str.Replace("\n", "\r\n"));
        }
        Cursor.Current = Cursors.Default;
    }
    
    public static void Main() {
        Application.Run(new QueryForm());
    }
}
