package examples

// Inside the backticks, try stjson, stbson, stbinding, stgorm, stbun, or sthuma + Tab.
// Use Ctrl+Space after a tag key prefix or after a comma in a tag value.
type Record struct {
	ID       string   `json:"id" bson:"_id,omitempty" dynamodbav:"id,omitempty"`
	Name     string   `json:"name" yaml:"name,omitempty" xml:"name,attr" toml:"name" msgpack:"name" redis:"name"`
	Email    string   `form:"email" binding:"required,email" validate:"required,email" conform:"trim,lower"`
	Port     int      `env:"PORT,required" envDefault:"8080"`
	Database string   `gorm:"column:database;size:255;not null" bun:"database,notnull"`
	Labels   []string `dynamodbav:"labels,stringset"`
}

type HumaInput struct {
	Name string `query:"name" required:"true" minLength:"1" maxLength:"80" doc:"Name to greet"`
	Age  int    `json:"age" minimum:"0" maximum:"130"`
}

type HCLConfig struct {
	Name string `hcl:"name,optional" default:"world"`
}
