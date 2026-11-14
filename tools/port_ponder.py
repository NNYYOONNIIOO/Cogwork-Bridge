"""Compile upstream MIT scene source into portable scene programs; never copy assets."""
import sys, json, re, math, copy, collections
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/ponder-tooling'))
import javalang
from javalang import tree as T
UP=Path(sys.argv[1] if len(sys.argv)>1 else 'D:/GitHub/Create-mc1.21.1-dev')
SRC=UP/'src/main/java/com/simibubi/create/infrastructure/ponder'
OUT=ROOT/'src/main/resources/assets/cogworkbridge/ponder'
METHODS={}; FIELDS={}
COLORS='white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black'.split()
ALIASES={'sail':'white_sail','orange_nixie_tube':'nixie_tube','smart_observer':'content_observer','threshold_switch':'stockpile_switch','package_postboxes':'postboxes'}
def obj(t,**kw):return dict(type=t,**kw)
def sym(v):return obj('symbol',v=v)
def en(v):return obj('enum',v=v.lower())
def vec(*v,kind='vec'):return obj(kind,v=list(v))
def pts(v):return v['v']
def dvec(d):return {'down':[0,-1,0],'up':[0,1,0],'north':[0,0,-1],'south':[0,0,1],'east':[1,0,0],'west':[-1,0,0]}[d['v']]
def sel(a,b):return obj('selection',v=[[x,y,z] for x in range(int(min(a[0],b[0])),int(max(a[0],b[0]))+1) for y in range(int(min(a[1],b[1])),int(max(a[1],b[1]))+1) for z in range(int(min(a[2],b[2])),int(max(a[2],b[2]))+1)])
def ast(v):
    if isinstance(v,javalang.ast.Node):return {'node':type(v).__name__,**{k:ast(getattr(v,k)) for k in v.attrs if getattr(v,k) not in (None,[],set())}}
    if isinstance(v,(list,tuple,set)):return [ast(x) for x in v]
    return v
class Return(Exception):
    def __init__(self,v):self.value=v
class Continue(Exception):pass
class Break(Exception):pass
class Compiler:
    def __init__(self,size):self.size=size;self.actions=[];self.time=0;self.link=0;self.owner='';self.scene_id='';self.title='';self.steps=0;self.unhandled=[];self.lambdas=[];self.object_index=0
    def emit(self,group,name,args):
        a={'at':self.time,'group':group,'op':name,'args':copy.deepcopy(args)};self.actions.append(a)
        return obj('builder',index=len(self.actions)-1)
    def run(self,owner,name,args):
        old=self.owner;self.owner=owner;m=METHODS[(owner,name)];env=dict(zip([p.name for p in m.parameters],args))
        try:self.statements(m.body,env)
        except Return as r:return r.value
        finally:self.owner=old
    def statements(self,body,env):
        if body is None:return
        for n in body if isinstance(body,list) else [body]:
            self.steps+=1
            if self.steps>80000:raise ValueError('instruction budget')
            if isinstance(n,(T.LocalVariableDeclaration,T.VariableDeclaration)):
                for d in n.declarators:env[d.name]=self.expr(d.initializer,env)
            elif isinstance(n,T.StatementExpression):
                result=self.expr(n.expression,env)
                if isinstance(result,dict) and result.get('type')=='call':self.unhandled.append(result)
            elif isinstance(n,T.BlockStatement):self.statements(n.statements,env)
            elif isinstance(n,T.ReturnStatement):raise Return(self.expr(n.expression,env))
            elif isinstance(n,T.IfStatement):self.statements(n.then_statement if self.expr(n.condition,env) else n.else_statement,env)
            elif isinstance(n,T.ForStatement):
                c=n.control
                if isinstance(c,T.EnhancedForControl):
                    for v in self.expr(c.iterable,env):
                        env[c.var.declarators[0].name]=v
                        try:self.statements(n.body,env)
                        except Continue:continue
                        except Break:break
                else:
                    for i in c.init if isinstance(c.init,list) else [c.init]:
                        if isinstance(i,T.VariableDeclaration):self.statements(i,env)
                        elif i:self.expr(i,env)
                    count=0
                    while c.condition is None or self.expr(c.condition,env):
                        count+=1
                        if count>1000:raise ValueError('loop budget')
                        try:self.statements(n.body,env)
                        except Continue:pass
                        except Break:break
                        for u in c.update or []:self.expr(u,env)
            elif isinstance(n,T.ContinueStatement):raise Continue()
            elif isinstance(n,T.BreakStatement):raise Break()
            else:raise ValueError('statement '+type(n).__name__)
    def static(self,p):
        if p in ('Vec3.ZERO','BlockPos.ZERO'):return vec(0,0,0,kind='pos' if p.startswith('BlockPos') else 'vec')
        if p=='ItemStack.EMPTY':return obj('item',id='minecraft:air',count=0)
        if p.startswith(('AllBlocks.','AllItems.','Blocks.','Items.')):
            g,k=p.split('.',1);k=ALIASES.get(k.lower(),k.lower());return obj('block' if g.endswith('Blocks') else 'item',id=('create:' if g.startswith('All') else 'minecraft:')+k,count=1,properties={})
        if p=='Iterate.trueAndFalse':return [True,False]
        if p=='Iterate.positiveAndNegative':return [1,-1]
        if p=='Iterate.horizontalDirections':return [en(x) for x in ['north','south','east','west']]
        if p=='Iterate.directions':return [en(x) for x in ['down','up','north','south','west','east']]
        if p=='Iterate.axes':return [en(x) for x in ['x','y','z']]
        if p.startswith(('Direction.','Pointing.','PonderPalette.','DyeColor.','Axis.','HeatLevel.','BeltPart.','Phase.','SignalState.','DoubleBlockHalf.','RailShape.')):return en(p.split('.')[-1])
        if p in ('Math.PI','Mth.PI'):return math.pi
        return sym(p)
    def resolve(self,p,env):
        if not p:return sym(self.owner)
        parts=p.split('.')
        if parts[0] in env:
            v=env[parts[0]]
            for x in parts[1:]:v=self.field(v,x)
            return v
        return self.static(p)
    def field(self,v,n):
        if isinstance(v,list) and n=='length':return len(v)
        if isinstance(v,dict):
            if v['type']=='symbol':return self.static(v['v']+'.'+n)
            if v['type'] in ('pos','vec') and n in 'xyz':return v['v']['xyz'.index(n)]
        return obj('field',target=v,name=n)
    def expr(self,n,env):
        if n is None:return None
        if isinstance(n,list):return [self.expr(x,env) for x in n]
        if isinstance(n,T.Literal):
            s=n.value
            if s in ('true','false'):v=s=='true'
            elif s=='null':v=None
            elif s.startswith(chr(34)):v=json.loads(s)
            elif s.startswith(chr(39)):v=s[1:-1]
            else:
                floating=s[-1:] in ('f','F','d','D') and not s.lower().startswith('0x')
                s=s.replace('_','')
                s=re.sub('[lL]$' if s.lower().startswith('0x') else '[fFdDlL]$','',s)
                v=float(s) if floating or '.' in s or 'e' in s.lower() and not s.lower().startswith('0x') else int(s,0) if s.lower().startswith('0x') else int(s)
        elif isinstance(n,T.MemberReference):
            v=self.field(self.resolve(n.qualifier,env),n.member) if n.qualifier else env.get(n.member,self.static(n.member))
        elif isinstance(n,T.MethodInvocation):v=self.call(self.resolve(n.qualifier,env),n.member,[self.expr(a,env) for a in n.arguments],env)
        elif isinstance(n,T.ClassCreator):
            k=n.type.name;a=[self.expr(x,env) for x in n.arguments]
            if k=='CreateSceneBuilder':v=obj('api',v='scene')
            elif k in ('BlockPos','Vec3','Vec3i'):v=vec(*(pts(a[0]) if len(a)==1 else a),kind='pos' if k!='Vec3' else 'vec')
            elif k=='AABB':v=obj('box',v=pts(a[0])+[x+1 for x in pts(a[0])] if len(a)==1 else pts(a[0])+pts(a[1]) if len(a)==2 else a)
            elif k=='ItemStack':v=copy.deepcopy(a[0]);v.update(type='item',count=a[1] if len(a)>1 else 1)
            elif k in ('ArrayList','LinkedList','List'):v=[]
            elif k=='CompoundTag':v=obj('nbt',v={})
            elif k=='MutableObject':v=obj('mutable',v=a[0] if a else None)
            else:
                self.object_index+=1;v=obj('construct',className=k,args=a,objectId=self.object_index)
        elif isinstance(n,T.BinaryOperation):
            a=self.expr(n.operandl,env);b=self.expr(n.operandr,env);o=n.operator
            if o in ('==','!='):v=(a==b) if o=='==' else (a!=b)
            elif isinstance(a,dict) or isinstance(b,dict):v=obj('binary',op=o,a=a,b=b)
            else:
                if o=='+':v=str(a)+str(b) if isinstance(a,str) or isinstance(b,str) else a+b
                elif o=='-':v=a-b
                elif o=='*':v=a*b
                elif o=='/':v=math.trunc(a/b) if isinstance(a,int) and isinstance(b,int) else a/b
                elif o=='%':v=a-math.trunc(a/b)*b
                elif o=='==':v=a==b
                elif o=='!=':v=a!=b
                elif o=='<':v=a<b
                elif o=='>':v=a>b
                elif o=='<=':v=a<=b
                elif o=='>=':v=a>=b
                elif o=='&&':v=a and b
                elif o=='||':v=a or b
                elif o=='^':v=a^b
                elif o=='&':v=a&b
                elif o=='|':v=a|b
                else:raise ValueError('operator '+o)
        elif isinstance(n,T.TernaryExpression):v=self.expr(n.if_true if self.expr(n.condition,env) else n.if_false,env)
        elif isinstance(n,T.Cast):
            v=self.expr(n.expression,env)
            if isinstance(v,(int,float)):
                if n.type.name in ('byte','short','int','long'):v=int(v)
                elif n.type.name in ('float','double'):v=float(v)
        elif isinstance(n,T.Assignment):
            v=self.expr(n.value,env);left=n.expressionl
            if isinstance(left,T.MemberReference) and not left.qualifier:
                if n.type=='+=':v=env[left.member]+v
                elif n.type=='-=':v=env[left.member]-v
                env[left.member]=v
            else:v=obj('assignment',target=self.expr(left,env),value=v,op=n.type)
        elif isinstance(n,T.ArrayInitializer):v=[self.expr(x,env) for x in n.initializers]
        elif isinstance(n,T.ArrayCreator):v=self.expr(n.initializer,env) if n.initializer else [None]*int(self.expr(n.dimensions[0],env))
        elif isinstance(n,T.ClassReference):v=obj('class',v=n.type.name)
        elif isinstance(n,T.LambdaExpression):
            names=[getattr(x,'name',None) or getattr(x,'member',None) for x in n.parameters]
            used={x.member for _,x in n if isinstance(x,T.MemberReference) and not x.qualifier}
            used.update(x.qualifier.split('.')[0] for _,x in n if isinstance(x,(T.MethodInvocation,T.MemberReference)) and x.qualifier)
            captures={k:v for k,v in env.items() if k in used and not (isinstance(v,dict) and v.get('type') in ('api','builder'))}
            v=obj('lambda',params=names,body=ast(n.body),captures=captures)
            v['compileIndex']=len(self.lambdas);self.lambdas.append((n,dict(env)))
        elif isinstance(n,T.MethodReference):v=obj('methodref',owner=ast(n.expression),method=ast(n.method))
        else:raise ValueError('expression '+type(n).__name__)
        for s in getattr(n,'selectors',None) or []:
            if isinstance(s,T.MethodInvocation):v=self.call(v,s.member,[self.expr(a,env) for a in s.arguments],env)
            elif isinstance(s,T.MemberReference):v=self.field(v,s.member)
            elif isinstance(s,T.ArraySelector):v=v[int(self.expr(s.index,env))]
            else:raise ValueError('selector '+type(s).__name__)
        for o in getattr(n,'prefix_operators',None) or []:
            if o=='-':v=-v if isinstance(v,(int,float)) else obj('unary',op=o,v=v)
            elif o=='!':v=not v
            elif o in ('++','--'):v+=1 if o=='++' else -1;env[n.member]=v
        for o in getattr(n,'postfix_operators',None) or []:
            if o in ('++','--'):env[n.member]=v+(1 if o=='++' else -1)
        return v
    def call(self,t,n,a,env):
        k=t.get('type') if isinstance(t,dict) else None;v=t.get('v') if isinstance(t,dict) else None
        if k=='api':
            if v=='scene':
                if n in ('world','overlay','effects','special','debug'):return obj('api',v=n)
                if n in ('idle','idleSeconds'):self.time+=int(a[0])*(20 if n=='idleSeconds' else 1);return None
                if n=='title':self.scene_id=a[0];self.title=a[1]
                if n=='getCurrentTime':return self.time
                return self.emit('scene',n,a)
            if v=='util':return obj('api',v=n)
            if v in ('vector','grid'):
                if n=='zero':return vec(0,0,0,kind='pos' if v=='grid' else 'vec')
                if n in ('at','of'):return vec(*a,kind='pos' if v=='grid' else 'vec')
                p=pts(a[0]) if len(a)==1 else a[:3]
                if n=='centerOf':return vec(*[x+.5 for x in p])
                if n=='topOf':return vec(p[0]+.5,p[1]+1,p[2]+.5)
                if n=='blockSurface':
                    p=pts(a[0]);d=dvec(a[1]);return vec(*[p[i]+.5+d[i]*.5 for i in range(3)])
            if v=='select':
                if n=='cuboid':return sel(pts(a[0]),[pts(a[0])[i]+pts(a[1])[i] for i in range(3)])
                if n=='position':return obj('selection',v=[list(pts(a[0]) if len(a)==1 else a)])
                if n=='fromTo':return sel(pts(a[0]),pts(a[1])) if len(a)==2 else sel(a[:3],a[3:])
                if n=='everywhere':return sel([0,0,0],[x-1 for x in self.size])
                if n in ('layer','layers','layersFrom'):
                    y=a[0];end=self.size[1]-1 if n=='layersFrom' else y+(a[1] if n=='layers' else 1)-1
                    return sel([0,y,0],[self.size[0]-1,end,self.size[2]-1])
                if n=='column':return sel([a[0],0,a[1]],[a[0],self.size[1]-1,a[1]])
            if v in ('world','effects','overlay','special','debug'):
                r=self.emit(v,n,a)
                if v=='world' and n in ('showIndependentSection','showIndependentSectionImmediately','makeSectionIndependent','createItemOnBelt','createItemEntity','createEntity') or v=='special' and n.startswith('create') or v=='effects' and n in ('simpleParticleEmitter','particleEmitterWithinBlockSpace'):
                    self.link+=1;self.actions[-1]['result']=self.link;return obj('link',id=self.link)
                return r
        if k=='builder':self.actions[t['index']].setdefault('chain',[]).append([n,a]);return t
        if k in ('vec','pos'):
            p=list(v)
            if n in ('above','below','north','south','east','west','relative'):
                distance=a[0] if a and n!='relative' else a[1] if len(a)>1 else 1
                d=dvec(a[0] if n=='relative' else en({'above':'up','below':'down'}.get(n,n)))
                return vec(*[p[i]+d[i]*distance for i in range(3)],kind=k)
            if n in ('add','subtract','scale','multiply','normalize','reverse','offset'):
                if n=='normalize':l=math.sqrt(sum(x*x for x in p));return vec(*[x/l if l else 0 for x in p],kind=k)
                if n in ('scale','multiply','reverse'):return vec(*[x*(-1 if n=='reverse' else a[0]) for x in p],kind=k)
                other=pts(a[0]) if len(a)==1 else a;return vec(*[p[i]+other[i]*(-1 if n=='subtract' else 1) for i in range(3)],kind=k)
            if n in ('getX','getY','getZ'):return p['XYZ'.index(n[-1])]
            if n=='toImmutable':return t
            if n=='distanceTo':return math.sqrt(sum((p[i]-pts(a[0])[i])**2 for i in range(3)))
        if k=='selection':
            if n=='forEach':
                node,captured=self.lambdas[a[0]['compileIndex']]
                for point in v:
                    scope=dict(captured);scope[a[0]['params'][0]]=vec(*point,kind='pos')
                    self.statements(node.body,scope) if isinstance(node.body,list) else self.expr(node.body,scope)
                return None
            if n=='copy':return copy.deepcopy(t)
            if n in ('add','substract','subtract'):
                s={tuple(p) for p in v};b={tuple(p) for p in a[0]['v']};return obj('selection',v=[list(p) for p in sorted(s|b if n=='add' else s-b)])
            if n=='getCenter':return vec(*[(min(p[i] for p in v)+max(p[i] for p in v)+1)/2 for i in range(3)])
        if k in ('block','item','state'):
            if n=='set':t['components']=dict(t.get('components',{}));t['components'][str(a[0].get('v',a[0]))]=a[1];return None
            if n in ('get','asStack','getDefaultState','defaultBlockState'):
                r=copy.deepcopy(t);r['type']='item' if n=='asStack' else 'state' if n.endswith('State') else k
                families={'dyed_sails':'_sail','dyed_valve_handles':'_valve_handle','postboxes':'_postbox','package_postboxes':'_postbox','table_cloths':'_table_cloth'}
                family=t['id'].split(':')[-1]
                if n=='get' and a and family in (*families,'nixie_tubes'):
                    color=a[0]['v']
                    r['id']='create:'+('nixie_tube' if family=='nixie_tubes' else color+families[family])
                    r['count']=1
                    if family=='nixie_tubes':r.setdefault('properties',{})['color']=a[0]
                elif a:r['count']=a[0]
                return r
            if n in ('setValue','withProperty','cycle'):
                r=copy.deepcopy(t);r['type']='state';key=(a[0].get('v','') if isinstance(a[0],dict) else str(a[0])).split('.')[-1].lower();r.setdefault('properties',{})[key]=a[1] if len(a)>1 else obj('cycle');return r
            if n in ('copy','copyWithCount'):
                r=copy.deepcopy(t)
                if a:r['count']=a[0]
                return r
        if k=='box':
            p=list(v)
            if n in ('inflate','deflate'):
                d=a if len(a)==3 else [a[0]]*3;s=-1 if n=='deflate' else 1;return obj('box',v=[p[i]-d[i]*s for i in range(3)]+[p[i+3]+d[i]*s for i in range(3)])
            if n=='move':
                d=pts(a[0]) if len(a)==1 else a;return obj('box',v=[p[i]+d[i%3] for i in range(6)])
            if n in ('expandTowards','contract'):
                d=pts(a[0]) if len(a)==1 else a;s=-1 if n=='contract' else 1;return obj('box',v=[p[i]+min(0,d[i])*s for i in range(3)]+[p[i+3]+max(0,d[i])*s for i in range(3)])
            if n=='getCenter':return vec(*[(p[i]+p[i+3])/2 for i in range(3)])
        if k=='enum':
            if n=='getOpposite':return en(dict(north='south',south='north',east='west',west='east',up='down',down='up')[v])
            if n in ('getClockWise','getCounterClockWise'):return en(['north','east','south','west'][(['north','east','south','west'].index(v)+(1 if n=='getClockWise' else -1))%4])
            if n=='getAxis':return en('x' if v in ('east','west') else 'y' if v in ('up','down') else 'z')
            if n=='getName':return v
            if n=='ordinal':return ['down','up','north','south','west','east'].index(v) if v in ('down','up','north','south','west','east') else COLORS.index(v)
            if n=='getNormal':return vec(*dvec(t),kind='pos')
            if n in ('getStepX','getStepY','getStepZ'):return dvec(t)['XYZ'.index(n[-1])]
        if k=='mutable':
            if n in ('set','setValue'):t['v']=a[0];return None
            if n in ('get','getValue'):return v
        if k=='construct' and t['className']=='FactoryPanelPosition' and n in ('pos','slot'):return t['args'][0 if n=='pos' else 1]
        if isinstance(t,list):
            if n=='add':t.append(a[0]);return True
            if n in ('get','getFirst','getSecond'):return t[int(a[0]) if a else 0 if n=='getFirst' else 1]
            if n=='size':return len(t)
            if n=='iterator':return list(t)
        if k=='symbol':
            owner=v.split('.')[-1]
            if (owner,n) in METHODS:return self.run(owner,n,a)
            if owner in ('ImmutableList','List','Couple','IntAttached') and n in ('of','create'):return list(a)
            if owner in ('DyeColor','Direction') and n=='values':return [en(c) for c in (COLORS if owner=='DyeColor' else ['down','up','north','south','west','east'])]
            if owner in ('Math','Mth'):
                if n=='clamp':return max(a[1],min(a[2],a[0]))
                if n in ('min','max'):return (min if n=='min' else max)(a)
                if n in ('abs','floor','ceil','sin','cos','sqrt'):return abs(a[0]) if n=='abs' else getattr(math,n)(a[0])
                if n=='lerp':return a[1]+a[0]*(a[2]-a[1])
            if owner in ('Vec3','VecHelper'):
                if n in ('atCenterOf','getCenterOf'):return vec(*[x+.5 for x in pts(a[0])])
                if n=='atLowerCornerOf':return vec(*pts(a[0]))
            if owner=='AABB' and n=='ofSize':return obj('box',v=[pts(a[0])[i]-a[i+1]/2 for i in range(3)]+[pts(a[0])[i]+a[i+1]/2 for i in range(3)])
            if owner=='Collections' and n=='shuffle':return None
            if owner=='Collections' and n=='reverse':a[0].reverse();return None
            if owner=='PackageItem' and n=='addAddress':a[0]['address']=a[1];return None
            if owner=='Collections' and n in ('singletonList','singleton'):return list(a)
            if owner=='ItemHandlerHelper' and n=='copyStackWithAmount':r=copy.deepcopy(a[0]);r['count']=a[1];return r
        return obj('call',target=t,name=n,args=a)
def main():
    import nbtlib
    for f in sorted((SRC/'scenes').rglob('*.java')):
        unit=javalang.parse.parse(f.read_text(encoding='utf8'))
        for _,node in unit:
            if isinstance(node,T.MethodDeclaration):METHODS[(f.stem,node.name)]=node
            elif isinstance(node,T.FieldDeclaration):
                for d in node.declarators:FIELDS[(f.stem,d.name)]=d.initializer
    registry=(SRC/'AllCreatePonderScenes.java').read_text(encoding='utf8');catalog=[]
    for statement in re.findall(r'\bHELPER\s*[.][^;]*;',registry,re.S):
        pre=statement.split('.addStoryBoard',1)[0];components=re.findall(r'All(?:Blocks|Items)\.([A-Z_]+)',pre) if '.forComponents' in pre else []
        if 'TrackMaterial.allBlocks' in pre:components=['TRACK']
        for m in re.finditer(r'\.addStoryBoard\((.*?)\)',statement,re.S):
            a=m.group(1);k=re.search(r'All(?:Blocks|Items)\.([A-Z_]+)',a);p=re.search(r'"([^"]+)"',a);fn=re.search(r'(\w+)::(\w+)',a)
            if not p or not fn:continue
            catalog.append(dict(structure=p.group(1),owner=fn.group(1),method=fn.group(2),components=[ALIASES.get(c.lower(),c.lower()) for c in ([k.group(1)] if k else components)],tags=[t.lower() for t in re.findall(r'AllCreatePonderTags\.([A-Z_]+)',a)]))
    OUT.mkdir(parents=True,exist_ok=True);programs={};issues=[];ops=collections.Counter()
    for row in catalog:
        key=row['owner']+'.'+row['method']
        if key in programs:continue
        size=[int(x) for x in nbtlib.load(UP/'src/main/resources/assets/create/ponder'/(row['structure']+'.nbt'))['size']];c=Compiler(size)
        try:c.run(row['owner'],row['method'],[obj('api',v='scene'),obj('api',v='util')]);status='translated'
        except Exception as ex:status='incomplete';issues.append(dict(scene=key,error=str(ex),completedActions=len(c.actions)))
        programs[key]=dict(status=status,id=c.scene_id,title=c.title,duration=c.time,actions=c.actions,unhandledSetup=c.unhandled);ops.update(a['group']+'.'+a['op'] for a in c.actions)
    tagsrc=re.sub(r'//[^\r\n]*','',(SRC/'AllCreatePonderTags.java').read_text(encoding='utf8'))
    tagids=dict(re.findall(r'(\w+)\s*=\s*loc\("([^"]+)"\)',tagsrc));tags=[]
    for match in re.finditer(r'helper\.registerTag\((\w+)\)(.*?)\.register\(\);',tagsrc,re.S):
        key,body=match.groups();title=re.search(r'\.title\("([^"]*)"\)',body);description=re.search(r'\.description\("([^"]*)"\)',body)
        icon=re.search(r'\.item\((AllBlocks|AllItems|Blocks|Items)\.(\w+)',body)
        if not title or not description or key not in tagids:continue
        tags.append(dict(id=tagids[key],title=title.group(1),description=description.group(1),index='.addToIndex()' in body,
            icon=('create:' if icon.group(1).startswith('All') else 'minecraft:')+icon.group(2).lower() if icon else 'create:cogwheel',components=[]))
    for match in re.finditer(r'(?:HELPER|itemHelper)\.addToTag\((\w+)\)([^;]*);',tagsrc,re.S):
        tag=next((t for t in tags if t['id']==tagids.get(match.group(1))),None)
        if tag is not None:
            tag['components'] += [('create:' if group.startswith('All') else 'minecraft:')+key.lower() for group,key in re.findall(r'\.add\((AllBlocks|AllItems|Blocks|Items)\.(\w+)',match.group(2))]
    for row in catalog:
        for tag in tags:
            if tag['id'] in row['tags']:tag['components']+=row['components']
    (OUT/'upstream-scenes.json').write_text(json.dumps(dict(source='Create 1.21.1',catalog=catalog,tags=tags,programs=programs),ensure_ascii=False,separators=(',',':')),encoding='utf8')
    iconsrc=(UP/'src/main/java/com/simibubi/create/foundation/gui/AllIcons.java').read_text(encoding='utf8');x=0;y=-1;icons={}
    for key,method in re.findall(r'(\w+)\s*=\s*(newRow|next)\(\)',iconsrc):
        if method=='newRow':x=0;y+=1
        else:x+=1
        icons[key]=[x,y]
    (OUT/'upstream-icons.json').write_text(json.dumps(icons,separators=(',',':')),encoding='utf8')
    report=dict(bindings=len(catalog),programs=len(programs),actions=sum(len(p['actions']) for p in programs.values()),incomplete=issues,unhandledSetup={k:p['unhandledSetup'] for k,p in programs.items() if p['unhandledSetup']},operations=dict(ops))
    (ROOT/'build/ponder-import-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
